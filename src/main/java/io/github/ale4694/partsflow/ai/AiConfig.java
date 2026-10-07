package io.github.ale4694.partsflow.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

/**
 * Provider independent AI configuration. What belongs to one provider lives in the {@code provider} package.
 * <p>
 * The RetryTemplate bean is static: the Spring AI auto-configuration needs it very early, while the context is
 * still being set up. A non-static @Bean method would force Spring to create this class too early ("Cannot
 * enhance @Configuration bean definition 'aiConfig'" warning at startup).
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
class AiConfig {

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
