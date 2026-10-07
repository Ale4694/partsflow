package io.github.ale4694.partsflow.ai.provider;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Everything specific to Google Gemini (LLM_PROVIDER=gemini, the default). Spring AI's own auto-configuration
 * creates the chat model; this class adds what we need around it.
 * <p>
 * The Client bean is static: Spring AI needs it very early, and a non-static @Bean method would force Spring to
 * create this class too early ("Cannot enhance @Configuration bean definition" warning at startup).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "partsflow.ai", name = "provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiConfiguration {

	/**
	 * Without this bean the Spring AI starter refuses to start when LLM_API_KEY is empty, which would take the
	 * whole application down. A placeholder lets the app start; {@code LlmGateway} never calls the model unless a
	 * real key is configured, so the placeholder is never sent anywhere.
	 * <p>
	 * The key is read from the {@link Environment}, NOT with {@code @Value}: this method is static and the bean is
	 * created very early, before Spring can resolve {@code ${...}} placeholders in {@code @Value}. The client would
	 * then hold the literal text of the placeholder and Gemini would answer "API key not valid".
	 */
	@Bean
	public static Client googleGenAiClient(Environment environment) {
		String apiKey = environment.getProperty("partsflow.ai.api-key", "");
		return Client.builder().apiKey(apiKey.isBlank() ? "not-configured" : apiKey).httpOptions(httpOptions()).build();
	}

	/**
	 * The SDK retries failed requests ON ITS OWN unless told otherwise: up to 5 attempts with waits of 1, 2, 4, 8 s
	 * on 408/429/5xx (one call measured: 5 provider requests in 38 seconds). That multiplies our own retries and
	 * spends the tiny free-tier quota (about 20 requests per day per model) on errors that cannot recover.
	 * Retrying is {@code LlmGateway}'s job, so the SDK makes exactly one attempt.
	 */
	public static HttpOptions httpOptions() {
		return HttpOptions.builder().retryOptions(HttpRetryOptions.builder().attempts(1).build()).build();
	}

	@Bean
	LlmErrorTranslator geminiErrorTranslator() {
		return new GeminiErrorTranslator();
	}
}
