package io.github.ale4694.partsflow.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.Client;
import io.github.ale4694.partsflow.TestcontainersConfiguration;
import io.github.ale4694.partsflow.ai.LlmGateway;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * LLM_PROVIDER=openai-compatible with complete settings: exactly the OpenAI chat model and error translator are
 * created, and nothing of Gemini. The address is a fake one: no request is ever sent.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
		"partsflow.ai.provider=openai-compatible",
		"partsflow.ai.api-key=fake-key",
		"partsflow.ai.base-url=http://localhost:9/v1",
		"partsflow.ai.model=some-model" })
class OpenAiCompatibleWiringTest {

	@Autowired
	ApplicationContext context;
	@Autowired
	LlmGateway gateway;

	@Test
	void createsOnlyTheOpenAiChatModelAndTranslator() {
		Map<String, ChatModel> models = context.getBeansOfType(ChatModel.class);

		assertThat(models).hasSize(1);
		assertThat(models.values().iterator().next().getClass().getSimpleName()).isEqualTo("OpenAiChatModel");
		assertThat(context.getBeansOfType(LlmErrorTranslator.class).values())
				.singleElement().isInstanceOf(OpenAiErrorTranslator.class);
		assertThat(context.getBeansOfType(Client.class)).isEmpty();
		assertThat(gateway.isConfigured()).isTrue();
	}
}
