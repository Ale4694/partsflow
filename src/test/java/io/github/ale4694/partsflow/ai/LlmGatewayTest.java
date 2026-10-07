package io.github.ale4694.partsflow.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.genai.errors.ClientException;
import com.google.genai.errors.ServerException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/** The model is a Mockito mock: no network, no API key. */
class LlmGatewayTest {

	record Answer(String value) {
	}

	private static final AiProperties PROPERTIES = new AiProperties(5, 5, 0.1, 10, 30000,
			new AiProperties.Retry(3, Duration.ofMillis(1), 2.0, Duration.ofSeconds(5)));

	private ChatModel model;
	private LlmGateway gateway;
	private ListAppender<ILoggingEvent> logs;
	private Logger gatewayLogger;

	@BeforeEach
	void setUp() {
		model = mock(ChatModel.class);
		when(model.getOptions()).thenReturn(ChatOptions.builder().build());
		gateway = new LlmGateway(ChatClient.builder(model).build(), "a-test-key", PROPERTIES);
		gatewayLogger = (Logger) LoggerFactory.getLogger(LlmGateway.class);
		logs = new ListAppender<>();
		logs.start();
		gatewayLogger.addAppender(logs);
		gatewayLogger.setLevel(Level.INFO);
	}

	@AfterEach
	void tearDown() {
		gatewayLogger.detachAppender(logs);
	}

	private ChatResponse reply(String text) {
		return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
	}

	private Answer ask() {
		return gateway.structured("test-op", "system prompt", "user prompt", Answer.class);
	}

	@Test
	void returnsTheStructuredAnswer() {
		when(model.call(any(Prompt.class))).thenReturn(reply("{\"value\": \"hello\"}"));

		assertThat(ask().value()).isEqualTo("hello");
	}

	@Test
	void withoutApiKeyTheModelIsNeverCalled() {
		LlmGateway disabled = new LlmGateway(ChatClient.builder(model).build(), "", PROPERTIES);

		assertThat(disabled.isConfigured()).isFalse();
		assertThatThrownBy(() -> disabled.structured("test-op", "s", "u", Answer.class))
				.isInstanceOf(AiUnavailableException.class)
				.hasMessageContaining("LLM_API_KEY")
				.extracting(e -> ((AiUnavailableException) e).reason()).isEqualTo(AiUnavailableException.Reason.KEY_MISSING);
		assertThatThrownBy(() -> disabled.converse("test-op", "s", "u", new Object()))
				.isInstanceOf(AiUnavailableException.class);
		verifyNoInteractions(model);
	}

	@Test
	void rateLimitIsRetriedWithBackoffUntilItSucceeds() {
		when(model.call(any(Prompt.class)))
				.thenThrow(new ClientException(429, "RESOURCE_EXHAUSTED", "quota"))
				.thenThrow(new ServerException(503, "UNAVAILABLE", "overloaded"))
				.thenReturn(reply("{\"value\": \"finally\"}"));

		assertThat(ask().value()).isEqualTo("finally");
		verify(model, times(3)).call(any(Prompt.class));
	}

	@Test
	void givesUpAfterTheLimitedNumberOfAttemptsWithA503() {
		when(model.call(any(Prompt.class))).thenThrow(new ClientException(429, "RESOURCE_EXHAUSTED", "quota"));

		assertThatThrownBy(this::ask)
				.isInstanceOf(AiUnavailableException.class)
				.hasMessageContaining("rate limited")
				.extracting(e -> ((AiUnavailableException) e).reason())
				.isEqualTo(AiUnavailableException.Reason.TEMPORARILY_UNAVAILABLE);
		verify(model, times(3)).call(any(Prompt.class));
	}

	@Test
	void requestsTheProviderRejectsAreNotRetried() {
		when(model.call(any(Prompt.class))).thenThrow(new ClientException(401, "UNAUTHENTICATED", "bad key"));

		assertThatThrownBy(this::ask)
				.isInstanceOf(AiUnavailableException.class)
				.hasMessageContaining("HTTP 401")
				.extracting(e -> ((AiUnavailableException) e).reason()).isEqualTo(AiUnavailableException.Reason.REJECTED);
		verify(model, times(1)).call(any(Prompt.class));
	}

	@Test
	void anAnswerThatIsNotValidJsonIsABadGatewayNotAnUnavailableService() {
		when(model.call(any(Prompt.class))).thenReturn(reply("Sorry, I cannot do that."));

		assertThatThrownBy(this::ask).isInstanceOf(LlmResponseException.class);
		verify(model, times(1)).call(any(Prompt.class));
	}

	@Test
	void everyCallIsLoggedWithoutTheDocumentContent() {
		when(model.call(any(Prompt.class))).thenReturn(reply("{\"value\": \"TOP-SECRET-ANSWER\"}"));

		gateway.structured("pdf-extraction", "system", "CONFIDENTIAL-INVOICE-TEXT", Answer.class);

		assertThat(logs.list).hasSize(1);
		String line = logs.list.getFirst().getFormattedMessage();
		assertThat(logs.list.getFirst().getLevel()).isEqualTo(Level.INFO);
		assertThat(line).contains("operation=pdf-extraction").contains("outcome=ok").contains("durationMs=");
		assertThat(line).doesNotContain("CONFIDENTIAL-INVOICE-TEXT").doesNotContain("TOP-SECRET-ANSWER")
				.doesNotContain("a-test-key");
	}

	@Test
	void aProviderRejectionIsLoggedAtWarnWithItsStatusAndMessage() {
		when(model.call(any(Prompt.class))).thenThrow(new ClientException(400, "INVALID_ARGUMENT",
				"tools[0].function_declarations[3].parameters.properties: should be non-empty for OBJECT type"));

		assertThatThrownBy(() -> gateway.structured("inventory-assistant", "system", "CONFIDENTIAL-QUESTION",
				Answer.class)).isInstanceOf(AiUnavailableException.class);

		ILoggingEvent warning = logs.list.stream().filter(e -> e.getLevel() == Level.WARN).findFirst().orElseThrow();
		assertThat(warning.getFormattedMessage()).contains("operation=inventory-assistant").contains("httpStatus=400")
				.contains("providerStatus=INVALID_ARGUMENT")
				.contains("should be non-empty for OBJECT type")
				.doesNotContain("a-test-key").doesNotContain("CONFIDENTIAL-QUESTION");
	}

	@Test
	void aLongProviderMessageIsTruncatedInTheLog() {
		when(model.call(any(Prompt.class))).thenThrow(new ClientException(400, "INVALID_ARGUMENT", "x".repeat(5000)));

		assertThatThrownBy(this::ask).isInstanceOf(AiUnavailableException.class);

		String warning = logs.list.stream().filter(e -> e.getLevel() == Level.WARN).findFirst().orElseThrow()
				.getFormattedMessage();
		assertThat(warning).hasSizeLessThan(800).endsWith("...");
	}

	@Test
	void temporaryErrorsAreAlsoLoggedWithTheProviderMessage() {
		when(model.call(any(Prompt.class)))
				.thenThrow(new ClientException(429, "RESOURCE_EXHAUSTED", "Quota exceeded for metric"))
				.thenReturn(reply("{\"value\": \"ok\"}"));

		ask();

		assertThat(logs.list.stream().filter(e -> e.getLevel() == Level.WARN).map(ILoggingEvent::getFormattedMessage))
				.singleElement().asString().contains("httpStatus=429").contains("Quota exceeded for metric");
	}

	private static final String DAILY_QUOTA_MESSAGE = "Quota exceeded for metric: "
			+ "generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 20, model: gemini-3.5-flash\n"
			+ "Please retry in 9h3m1.2s.";

	@Test
	void aDailyQuotaFailsAtOnceWithoutRetryingAndSaysHowLongToWait() {
		when(model.call(any(Prompt.class))).thenThrow(new ClientException(429, "RESOURCE_EXHAUSTED", DAILY_QUOTA_MESSAGE));

		assertThatThrownBy(this::ask)
				.isInstanceOfSatisfying(AiUnavailableException.class, e -> {
					assertThat(e.reason()).isEqualTo(AiUnavailableException.Reason.DAILY_QUOTA_EXHAUSTED);
					assertThat(e.retryAfter()).isEqualTo(Duration.ofHours(9).plusMinutes(3).plusMillis(1200));
					assertThat(e.getMessage()).contains("daily quota").contains("about 9 hours");
				});
		verify(model, times(1)).call(any(Prompt.class));
	}

	@Test
	void aDailyQuotaWithoutADelayAlsoFailsAtOnce() {
		when(model.call(any(Prompt.class))).thenThrow(new ClientException(429, "RESOURCE_EXHAUSTED",
				"Quota exceeded {\"quotaId\":\"GenerateRequestsPerDayPerProjectPerModel-FreeTier\"}"));

		assertThatThrownBy(this::ask)
				.isInstanceOfSatisfying(AiUnavailableException.class, e -> {
					assertThat(e.reason()).isEqualTo(AiUnavailableException.Reason.DAILY_QUOTA_EXHAUSTED);
					assertThat(e.retryAfter()).isNull();
				});
		verify(model, times(1)).call(any(Prompt.class));
	}

	@Test
	void aRateLimitThatAsksForALongWaitFailsAtOnce() {
		// the test configuration retries only waits up to 5 seconds
		when(model.call(any(Prompt.class))).thenThrow(new ClientException(429, "RESOURCE_EXHAUSTED",
				"Too many requests. Details: {\"retryDelay\":\"33s\"}"));

		assertThatThrownBy(this::ask)
				.isInstanceOfSatisfying(AiUnavailableException.class, e -> {
					assertThat(e.reason()).isEqualTo(AiUnavailableException.Reason.RATE_LIMITED);
					assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(33));
					assertThat(e.getMessage()).contains("about 33 seconds");
				});
		verify(model, times(1)).call(any(Prompt.class));
	}

	@Test
	void aRateLimitThatPassesQuicklyIsRetriedAfterTheSuggestedWait() {
		when(model.call(any(Prompt.class)))
				.thenThrow(new ClientException(429, "RESOURCE_EXHAUSTED", "Slow down. Please retry in 20ms."))
				.thenReturn(reply("{\"value\": \"after the wait\"}"));

		long started = System.nanoTime();
		assertThat(ask().value()).isEqualTo("after the wait");

		verify(model, times(2)).call(any(Prompt.class));
		assertThat((System.nanoTime() - started) / 1_000_000).isGreaterThanOrEqualTo(20);
	}

	@Test
	void highDemandErrorsAreStillRetriedWithBackoff() {
		when(model.call(any(Prompt.class)))
				.thenThrow(new ServerException(503, "UNAVAILABLE", "This model is currently experiencing high demand."))
				.thenThrow(new ServerException(503, "UNAVAILABLE", "This model is currently experiencing high demand."))
				.thenReturn(reply("{\"value\": \"calmer now\"}"));

		assertThat(ask().value()).isEqualTo("calmer now");
		verify(model, times(3)).call(any(Prompt.class));
	}

	@Test
	void persistentHighDemandGivesUpAfterTheLimitedAttemptsAsTemporarilyUnavailable() {
		when(model.call(any(Prompt.class)))
				.thenThrow(new ServerException(503, "UNAVAILABLE", "This model is currently experiencing high demand."));

		assertThatThrownBy(this::ask)
				.isInstanceOfSatisfying(AiUnavailableException.class, e -> {
					assertThat(e.reason()).isEqualTo(AiUnavailableException.Reason.TEMPORARILY_UNAVAILABLE);
					assertThat(e.retryAfter()).isNull();
				});
		verify(model, times(3)).call(any(Prompt.class));
	}
}
