package io.github.ale4694.partsflow.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.errors.ClientException;
import com.google.genai.errors.ServerException;
import io.github.ale4694.partsflow.ai.provider.LlmFailure.Kind;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Errors of the Google GenAI SDK (Gemini), built by hand. */
class GeminiErrorTranslatorTest {

	private final GeminiErrorTranslator translator = new GeminiErrorTranslator();

	private LlmFailure translate(Throwable error) {
		return translator.translate(error).orElseThrow();
	}

	@Test
	void anInvalidKeyIsRejected() {
		LlmFailure failure = translate(new ClientException(400, "INVALID_ARGUMENT", "API key not valid. Please pass a valid API key."));

		assertThat(failure.kind()).isEqualTo(Kind.REJECTED);
		assertThat(failure.httpStatus()).isEqualTo(400);
		assertThat(failure.providerStatus()).isEqualTo("INVALID_ARGUMENT");
		assertThat(failure.providerMessage()).contains("API key not valid");
	}

	@Test
	void aModelThatDoesNotExistIsRejected() {
		LlmFailure failure = translate(new ClientException(404, "NOT_FOUND", "models/x is not found for API version v1beta"));

		assertThat(failure.kind()).isEqualTo(Kind.REJECTED);
	}

	@Test
	void aDailyQuotaCarriesItsDelay() {
		LlmFailure failure = translate(new ClientException(429, "RESOURCE_EXHAUSTED",
				"Quota exceeded for metric: generate_content_free_tier_requests, limit: 20\nPlease retry in 9h3m1.2s."));

		assertThat(failure.kind()).isEqualTo(Kind.DAILY_QUOTA);
		assertThat(failure.suggestedDelay()).isEqualTo(Duration.ofHours(9).plusMinutes(3).plusMillis(1200));
	}

	@Test
	void aShortRateLimitIsRateLimitedWithItsDelay() {
		LlmFailure failure = translate(new ClientException(429, "RESOURCE_EXHAUSTED", "Too many requests. Details: {\"retryDelay\":\"33s\"}"));

		assertThat(failure.kind()).isEqualTo(Kind.RATE_LIMITED);
		assertThat(failure.suggestedDelay()).isEqualTo(Duration.ofSeconds(33));
	}

	@Test
	void aRateLimitWithoutAnyHintHasNoDelay() {
		LlmFailure failure = translate(new ClientException(429, "RESOURCE_EXHAUSTED", "You exceeded your current quota"));

		assertThat(failure.kind()).isEqualTo(Kind.RATE_LIMITED);
		assertThat(failure.suggestedDelay()).isNull();
	}

	@Test
	void highDemandIsTemporary() {
		LlmFailure failure = translate(new ServerException(503, "UNAVAILABLE", "This model is currently experiencing high demand."));

		assertThat(failure.kind()).isEqualTo(Kind.OVERLOADED);
		assertThat(failure.httpStatus()).isEqualTo(503);
	}

	@Test
	void aConnectionProblemIsANetworkFailureWithoutStatus() {
		LlmFailure failure = translate(new RuntimeException("call failed", new IOException("connection reset")));

		assertThat(failure.kind()).isEqualTo(Kind.NETWORK);
		assertThat(failure.httpStatus()).isZero();
	}

	@Test
	void otherExceptionsAreNotProviderErrors() {
		assertThat(translator.translate(new IllegalStateException("a bug of ours"))).isEmpty();
	}
}
