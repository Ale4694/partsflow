package io.github.ale4694.partsflow.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AiConfigTest {

	@Test
	void usesTheConfiguredKey() {
		MockEnvironment environment = new MockEnvironment().withProperty("spring.ai.google.genai.api-key", "fake-key");

		assertThat(AiConfig.googleGenAiClient(environment).apiKey()).isEqualTo("fake-key");
	}

	@Test
	void anEmptyOrMissingKeyGivesAPlaceholderSoTheApplicationStillStarts() {
		assertThat(AiConfig.googleGenAiClient(new MockEnvironment()).apiKey()).isEqualTo("not-configured");
		assertThat(AiConfig.googleGenAiClient(
				new MockEnvironment().withProperty("spring.ai.google.genai.api-key", " ")).apiKey())
				.isEqualTo("not-configured");
	}
}
