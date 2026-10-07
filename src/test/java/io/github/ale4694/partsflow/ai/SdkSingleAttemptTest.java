package io.github.ale4694.partsflow.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The SDK retries on its own by default (5 attempts, 38 seconds for one call measured). This test uses a local
 * HTTP server that always answers 429 and counts the requests: no network, no real key.
 */
class SdkSingleAttemptTest {

	private HttpServer server;
	private final AtomicInteger requests = new AtomicInteger();

	@BeforeEach
	void startServer() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			requests.incrementAndGet();
			byte[] body = "{\"error\":{\"code\":429,\"message\":\"Quota exceeded\",\"status\":\"RESOURCE_EXHAUSTED\"}}"
					.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(429, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@Test
	void theConfiguredClientSendsExactlyOneRequestWhenTheProviderAnswers429() {
		String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
		long started = System.nanoTime();
		try (Client client = Client.builder().apiKey("fake-key")
				.httpOptions(AiConfig.httpOptions().toBuilder().baseUrl(baseUrl).build()).build()) {
			assertThatThrownBy(() -> client.models.generateContent("some-model", "hello", null))
					.isInstanceOf(ApiException.class)
					.extracting(e -> ((ApiException) e).code()).isEqualTo(429);
		}

		assertThat(requests.get()).isEqualTo(1);
		assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(5_000);
	}
}
