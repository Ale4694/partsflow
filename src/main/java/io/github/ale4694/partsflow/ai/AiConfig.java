package io.github.ale4694.partsflow.ai;

import com.google.genai.Client;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
	 */
	@Bean
	static Client googleGenAiClient(@Value("${spring.ai.google.genai.api-key:}") String apiKey) {
		return Client.builder().apiKey(apiKey.isBlank() ? "not-configured" : apiKey).build();
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
