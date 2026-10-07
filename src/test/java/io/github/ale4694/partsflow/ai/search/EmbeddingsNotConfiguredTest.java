package io.github.ale4694.partsflow.ai.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;

/** No API key (the test default): items save normally, nothing is sent anywhere, the indexer says why. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EmbeddingsNotConfiguredTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	FakeEmbeddingModel model;
	@Autowired
	ItemEmbeddingIndexer indexer;
	@Autowired
	ItemSearchService search;

	@Test
	void searchFallsBackToTextAndSaysItIsNotConfigured() {
		model.reset();

		ItemSearchService.SearchResult result = search.search("filtro olio", 5, true);

		assertThat(result.mode()).isEqualTo(ItemSearchService.Mode.TEXT);
		assertThat(result.fallbackReason()).isEqualTo(ItemSearchService.FallbackReason.NOT_CONFIGURED);
		assertThat(model.batchSizes()).isEmpty();
	}

	@Test
	void itemsSaveWithoutEmbeddingsAndTheIndexerReportsNotConfigured() throws Exception {
		model.reset();

		mvc.perform(post("/api/items").contentType(MediaType.APPLICATION_JSON).content("""
				{"code": "NOKEY-1", "description": "Filtro olio", "unit": "PZ", "reorderThreshold": 0}"""))
				.andExpect(status().isCreated());

		assertThat(model.batchSizes()).isEmpty();
		assertThat(indexer.indexStale().state()).isEqualTo(ItemEmbeddingIndexer.State.NOT_CONFIGURED);
		mvc.perform(get("/api/ai/embeddings/status")).andExpect(jsonPath("$.configured").value(false));
		mvc.perform(post("/api/ai/embeddings/reindex")).andExpect(status().isOk())
				.andExpect(jsonPath("$.state").value("NOT_CONFIGURED"));
	}
}
