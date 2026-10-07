package io.github.ale4694.partsflow.ai.search;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.ale4694.partsflow.TestcontainersConfiguration;
import io.github.ale4694.partsflow.catalog.Item;
import io.github.ale4694.partsflow.catalog.ItemRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** GET /api/items/search: the shape of the answer, the two modes and the validation. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = { "partsflow.ai.api-key=fake-key", "partsflow.embeddings.model=controller-test-model" })
class ItemSearchControllerTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	ItemRepository items;
	@Autowired
	ItemEmbeddingIndexer indexer;
	@Autowired
	FakeEmbeddingModel model;

	@BeforeEach
	void catalog() {
		if (items.count() == 0) {
			items.save(new Item("CTL-1", "Cartuccia lubrificante motore 1.2 FIRE", "PZ", BigDecimal.ZERO));
			items.save(new Item("CTL-2", "Pastiglie freni anteriori", "PZ", BigDecimal.ZERO));
			indexer.indexStale();
		}
		model.reset();
		indexer.resume();
	}

	@Test
	void hybridSearchReturnsScoredResultsAndTheMode() throws Exception {
		mvc.perform(get("/api/items/search").param("q", "filtro olio Fiat Panda"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mode").value("HYBRID"))
				.andExpect(jsonPath("$.fallbackReason").doesNotExist())
				.andExpect(jsonPath("$.results[?(@.code == 'CTL-1')].description").value("Cartuccia lubrificante motore 1.2 FIRE"))
				.andExpect(jsonPath("$.results[0].score").isNumber())
				.andExpect(jsonPath("$.results[0].quantity").isNumber())
				.andExpect(jsonPath("$.results[?(@.code == 'CTL-1')].vectorScore").isNotEmpty());
	}

	@Test
	void textModeNeverUsesEmbeddings() throws Exception {
		mvc.perform(get("/api/items/search").param("q", "pastiglie freni").param("mode", "text"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mode").value("TEXT"))
				.andExpect(jsonPath("$.fallbackReason").doesNotExist())
				.andExpect(jsonPath("$.results[0].code").value("CTL-2"))
				.andExpect(jsonPath("$.results[0].vectorScore").doesNotExist());
		org.assertj.core.api.Assertions.assertThat(model.batchSizes()).isEmpty();
	}

	@Test
	void theLimitIsRespectedAndClamped() throws Exception {
		mvc.perform(get("/api/items/search").param("q", "motore freni").param("limit", "1"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.results.length()").value(1));
		mvc.perform(get("/api/items/search").param("q", "motore").param("limit", "-5"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.results.length()").value(1));
	}

	@Test
	void anUnknownModeIsABadRequestInItalian() throws Exception {
		mvc.perform(get("/api/items/search").param("q", "filtro").param("mode", "magic"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Modalità di ricerca non valida")));
	}

	@Test
	void aMissingOrTooLongQueryIsABadRequest() throws Exception {
		mvc.perform(get("/api/items/search")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/items/search").param("q", "x".repeat(201)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("troppo lunga")));
	}

	@Test
	void theExistingItemListIsUnchanged() throws Exception {
		mvc.perform(get("/api/items").param("q", "pastiglie"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].code").value("CTL-2"))
				.andExpect(jsonPath("$.totalElements").value(1));
	}
}
