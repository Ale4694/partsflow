package io.github.ale4694.partsflow.ai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.ale4694.partsflow.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** No API key (forced empty, whatever the environment says): the app still starts and only AI endpoints answer 503. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = { "spring.ai.google.genai.api-key=", "partsflow.ai.api-key=" })
class AiDisabledIntegrationTest {

	@Autowired
	MockMvc mvc;

	@Test
	void aiEndpointsAnswer503WithAProblemDetail() throws Exception {
		mvc.perform(post("/api/ai/assistant").contentType(MediaType.APPLICATION_JSON)
				.content("{\"question\": \"How much brake fluid do we have?\"}"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.status").value(503))
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("LLM_API_KEY")))
				.andExpect(jsonPath("$.code").value("AI_KEY_MISSING"));

		mvc.perform(multipart("/api/ai/imports/pdf")
				.file(new MockMultipartFile("file", "invoice.pdf", "application/pdf", new byte[] {1, 2, 3})))
				.andExpect(status().isServiceUnavailable());

		mvc.perform(post("/api/ai/imports/1/suggest-matches")).andExpect(status().isServiceUnavailable());
	}

	@Test
	void statusEndpointReportsThatAiIsNotAvailable() throws Exception {
		mvc.perform(get("/api/ai/status")).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
	}

	@Test
	void theRestOfTheApplicationKeepsWorking() throws Exception {
		mvc.perform(get("/api/items")).andExpect(status().isOk());
		mvc.perform(get("/api/imports")).andExpect(status().isOk());
		mvc.perform(get("/api/inventory/low-stock")).andExpect(status().isOk());
	}
}
