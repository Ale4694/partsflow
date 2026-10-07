package io.github.ale4694.partsflow.ai.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Everything specific to OpenAI-compatible services (LLM_PROVIDER=openai-compatible): OpenAI itself, Mistral, Groq,
 * DeepSeek, OpenRouter... Spring AI's OpenAI auto-configuration creates the chat model from
 * {@code spring.ai.openai.*} (see application.yml, which maps LLM_BASE_URL, LLM_API_KEY and LLM_MODEL onto it and
 * sets {@code max-retries: 0}: the OpenAI SDK retries on its own like the Gemini one, and only the gateway should).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "partsflow.ai", name = "provider", havingValue = "openai-compatible")
class OpenAiCompatibleConfiguration {

	@Bean
	LlmErrorTranslator openAiErrorTranslator() {
		return new OpenAiErrorTranslator();
	}
}
