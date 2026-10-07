package io.github.ale4694.partsflow.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.Client;
import io.github.ale4694.partsflow.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * The key that the SDK client really holds must be the configured one. Nothing here calls the provider:
 * the key is a fake value, and the check is on the client object itself.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = { "spring.ai.google.genai.api-key=fake-key-for-this-test",
		"partsflow.ai.api-key=fake-key-for-this-test" })
class GenAiClientKeyTest {

	@Autowired
	Client client;

	@Test
	void theClientUsesTheConfiguredApiKeyNotAnUnresolvedPlaceholder() {
		assertThat(client.apiKey()).isEqualTo("fake-key-for-this-test");
	}
}
