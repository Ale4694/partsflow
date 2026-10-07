package io.github.ale4694.partsflow.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import io.github.ale4694.partsflow.TestcontainersConfiguration;
import io.github.ale4694.partsflow.ai.AiUnavailableException;
import io.github.ale4694.partsflow.ai.AiUnavailableException.Reason;
import io.github.ale4694.partsflow.ai.LlmGateway;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * The whole OpenAI-compatible path with the real Spring AI and OpenAI SDK, against a local fake server that
 * answers with errors (no network, no real key): the SDK must make exactly one request per call, and the gateway
 * must react to each provider error as it does for Gemini.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
		"partsflow.ai.provider=openai-compatible",
		"partsflow.ai.api-key=fake-key",
		"partsflow.ai.model=some-model",
		"partsflow.ai.retry.initial-backoff=10ms" })
class OpenAiCompatibleGatewayTest {

	record Answer(String value) {
	}

	private static final HttpServer SERVER = startServer();
	private static final AtomicInteger REQUESTS = new AtomicInteger();
	private static final AtomicReference<Reply> REPLY = new AtomicReference<>();

	private record Reply(int status, String retryAfter, String body) {
	}

	private static HttpServer startServer() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/", exchange -> {
				REQUESTS.incrementAndGet();
				Reply reply = REPLY.get();
				byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().add("Content-Type", "application/json");
				if (reply.retryAfter() != null) {
					exchange.getResponseHeaders().add("Retry-After", reply.retryAfter());
				}
				exchange.sendResponseHeaders(reply.status(), body.length);
				exchange.getResponseBody().write(body);
				exchange.close();
			});
			server.start();
			return server;
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	@DynamicPropertySource
	static void serverAddress(DynamicPropertyRegistry registry) {
		registry.add("partsflow.ai.base-url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/v1");
	}

	@AfterAll
	static void stopServer() {
		SERVER.stop(0);
	}

	@Autowired
	LlmGateway gateway;

	@BeforeEach
	void reset() {
		REQUESTS.set(0);
	}

	private static String error(String message, String code) {
		return "{\"error\":{\"message\":\"" + message + "\",\"type\":\"error\",\"param\":null,\"code\":"
				+ (code == null ? "null" : "\"" + code + "\"") + "}}";
	}

	private AiUnavailableException failure() {
		AiUnavailableException[] caught = new AiUnavailableException[1];
		assertThatThrownBy(() -> gateway.structured("test-op", "system", "user", Answer.class))
				.isInstanceOfSatisfying(AiUnavailableException.class, e -> caught[0] = e);
		return caught[0];
	}

	@Test
	void outOfCreditFailsAtOnceAfterASingleRequest() {
		REPLY.set(new Reply(429, null, error("You exceeded your current quota, please check your plan and billing details.",
				"insufficient_quota")));

		assertThat(failure().reason()).isEqualTo(Reason.DAILY_QUOTA_EXHAUSTED);
		assertThat(REQUESTS.get()).isEqualTo(1);
	}

	@Test
	void anInvalidKeyIsRejectedAfterASingleRequest() {
		REPLY.set(new Reply(401, null, error("Incorrect API key provided", "invalid_api_key")));

		assertThat(failure().reason()).isEqualTo(Reason.REJECTED);
		assertThat(REQUESTS.get()).isEqualTo(1);
	}

	@Test
	void aModelThatDoesNotExistIsRejectedAfterASingleRequest() {
		REPLY.set(new Reply(404, null, error("The model `some-model` does not exist", "model_not_found")));

		assertThat(failure().reason()).isEqualTo(Reason.REJECTED);
		assertThat(REQUESTS.get()).isEqualTo(1);
	}

	@Test
	void aRateLimitWithALongWaitFailsAtOnceAndSaysHowLong() {
		REPLY.set(new Reply(429, "300", error("Rate limit reached for requests", "rate_limit_exceeded")));

		AiUnavailableException failure = failure();

		assertThat(failure.reason()).isEqualTo(Reason.RATE_LIMITED);
		assertThat(failure.retryAfter().toSeconds()).isEqualTo(300);
		assertThat(REQUESTS.get()).isEqualTo(1);
	}

	@Test
	void aShortRateLimitIsRetriedOnlyByTheGatewayNotByTheSdk() {
		REPLY.set(new Reply(429, "1", error("Rate limit reached for requests", "rate_limit_exceeded")));

		assertThat(failure().reason()).isEqualTo(Reason.TEMPORARILY_UNAVAILABLE);
		// the gateway's 3 attempts, one request each: the SDK's own retries are switched off
		assertThat(REQUESTS.get()).isEqualTo(3);
	}

	@Test
	void anOverloadedServiceIsRetriedWithBackoffThenGivesUp() {
		REPLY.set(new Reply(503, null, error("The engine is currently overloaded", null)));

		assertThat(failure().reason()).isEqualTo(Reason.TEMPORARILY_UNAVAILABLE);
		assertThat(REQUESTS.get()).isEqualTo(3);
	}
}
