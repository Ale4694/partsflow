package io.github.ale4694.partsflow.ai;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

/**
 * The Client and RetryTemplate beans are static: the Spring AI auto-configuration needs them very early, while
 * the context is still being set up. A non-static @Bean method would force Spring to create this class too
 * early ("Cannot enhance @Configuration bean definition 'aiConfig'" warning at startup).
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
class AiConfig {

	/**
	 * Without this bean the Spring AI starter refuses to start when LLM_API_KEY is empty, which would take the
	 * whole application down. A placeholder lets the app start; {@link LlmGateway} never calls the model unless a
	 * real key is configured, so the placeholder is never sent anywhere.
	 * <p>
	 * The key is read from the {@link Environment}, NOT with {@code @Value}: this method is static and the bean is
	 * created very early, before Spring can resolve {@code ${...}} placeholders in {@code @Value}. The client would
	 * then hold the literal text "${spring.ai.google.genai.api-key:}" and Gemini would answer "API key not valid".
	 */
	@Bean
	static Client googleGenAiClient(Environment environment) {
		String apiKey = environment.getProperty("spring.ai.google.genai.api-key", "");
		return Client.builder().apiKey(apiKey.isBlank() ? "not-configured" : apiKey).httpOptions(httpOptions()).build();
	}

	/**
	 * The SDK retries failed requests ON ITS OWN unless told otherwise: up to 5 attempts with waits of 1, 2, 4, 8 s
	 * on 408/429/5xx (one call measured: 5 provider requests in 38 seconds). That multiplies our own retries and
	 * spends the tiny free-tier quota (about 20 requests per day per model) on errors that cannot recover.
	 * Retrying is {@link LlmGateway}'s job, so the SDK makes exactly one attempt.
	 */
	static HttpOptions httpOptions() {
		return HttpOptions.builder().retryOptions(HttpRetryOptions.builder().attempts(1).build()).build();
	}

	/**
	 * Spring AI retries failed calls on its own for minutes by default. Retrying is {@link LlmGateway}'s job
	 * (short, bounded, then a clear 503), so the built-in retry is switched off.
	 */
	@Bean
	static RetryTemplate aiRetryTemplate() {
		return new RetryTemplate(RetryPolicy.withMaxRetries(0));
	}

	@Bean
	ChatClient aiChatClient(ChatClient.Builder builder) {
		return builder.build();
	}
}
