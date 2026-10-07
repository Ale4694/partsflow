package io.github.ale4694.partsflow.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.ale4694.partsflow.TestcontainersConfiguration;
import io.github.ale4694.partsflow.ai.LlmGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * LLM_PROVIDER=openai-compatible with an API key but without LLM_BASE_URL and LLM_MODEL: the application must
 * still start; the AI is "not configured" and its endpoints answer 503 while everything else works.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
		"partsflow.ai.provider=openai-compatible",
		"partsflow.ai.api-key=fake-key",
		"partsflow.ai.base-url=",
		"partsflow.ai.model=" })
class OpenAiCompatibleIncompleteConfigTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	LlmGateway gateway;

	@Test
	void theApplicationStartsAndTheAiIsNotConfigured() throws Exception {
		assertThat(gateway.isConfigured()).isFalse();

		mvc.perform(get("/api/ai/status")).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
		mvc.perform(post("/api/ai/assistant").contentType(MediaType.APPLICATION_JSON)
				.content("{\"question\": \"Quanti pezzi abbiamo?\"}"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("AI_KEY_MISSING"))
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("LLM_BASE_URL")));
		mvc.perform(get("/api/items")).andExpect(status().isOk());
	}
}
