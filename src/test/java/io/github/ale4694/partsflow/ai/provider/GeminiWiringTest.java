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

/** LLM_PROVIDER=gemini (the default): exactly the Gemini chat model and error translator are created. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = { "partsflow.ai.provider=gemini", "partsflow.ai.api-key=fake-key" })
class GeminiWiringTest {

	@Autowired
	ApplicationContext context;
	@Autowired
	LlmGateway gateway;

	@Test
	void createsOnlyTheGeminiChatModelAndTranslator() {
		Map<String, ChatModel> models = context.getBeansOfType(ChatModel.class);

		assertThat(models).hasSize(1);
		assertThat(models.values().iterator().next().getClass().getSimpleName()).isEqualTo("GoogleGenAiChatModel");
		assertThat(context.getBeansOfType(LlmErrorTranslator.class).values())
				.singleElement().isInstanceOf(GeminiErrorTranslator.class);
		assertThat(context.getBeansOfType(Client.class)).hasSize(1);
		assertThat(gateway.isConfigured()).isTrue();
	}
}
