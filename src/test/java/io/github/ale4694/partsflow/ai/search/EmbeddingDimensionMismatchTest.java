package io.github.ale4694.partsflow.ai.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.ale4694.partsflow.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The database column was created with 768 numbers (what Flyway was told) but the application is now configured
 * for 1024, as happens when LLM_EMBEDDING_DIMENSIONS changes after the first start. The application must still
 * start, and the semantic search must switch itself off instead of failing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
		"partsflow.ai.api-key=fake-key",
		"spring.flyway.placeholders.embeddingDimensions=768",
		"partsflow.embeddings.dimensions=1024" })
class EmbeddingDimensionMismatchTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	EmbeddingSchemaGuard guard;
	@Autowired
	ItemEmbeddingIndexer indexer;
	@Autowired
	FakeEmbeddingModel model;

	@Test
	void theGuardDetectsItAndTheIndexerStops() {
		model.reset();

		assertThat(guard.columnDimensions()).isEqualTo(768);
		assertThat(guard.dimensionsMatch()).isFalse();
		assertThat(indexer.indexStale().state()).isEqualTo(ItemEmbeddingIndexer.State.DIMENSION_MISMATCH);
		assertThat(model.batchSizes()).isEmpty();
	}

	@Test
	void theStatusEndpointExplainsIt() throws Exception {
		mvc.perform(get("/api/ai/embeddings/status")).andExpect(status().isOk())
				.andExpect(jsonPath("$.dimensions").value(1024))
				.andExpect(jsonPath("$.columnDimensions").value(768))
				.andExpect(jsonPath("$.dimensionsMatch").value(false));
	}
}
