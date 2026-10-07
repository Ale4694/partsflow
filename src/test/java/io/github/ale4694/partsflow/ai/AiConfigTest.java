package io.github.ale4694.partsflow.ai;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ale4694.partsflow.ai.provider.GeminiConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** The Gemini client is built from partsflow.ai.api-key (LLM_API_KEY), read from the Environment. */
class AiConfigTest {

	@Test
	void usesTheConfiguredKey() {
		MockEnvironment environment = new MockEnvironment().withProperty("partsflow.ai.api-key", "fake-key");

		assertThat(GeminiConfiguration.googleGenAiClient(environment).apiKey()).isEqualTo("fake-key");
	}

	@Test
	void anEmptyOrMissingKeyGivesAPlaceholderSoTheApplicationStillStarts() {
		assertThat(GeminiConfiguration.googleGenAiClient(new MockEnvironment()).apiKey()).isEqualTo("not-configured");
		assertThat(GeminiConfiguration.googleGenAiClient(
				new MockEnvironment().withProperty("partsflow.ai.api-key", " ")).apiKey())
				.isEqualTo("not-configured");
	}
}
