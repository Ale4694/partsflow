package io.github.ale4694.partsflow.ai.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.genai.errors.ServerException;
import io.github.ale4694.partsflow.TestcontainersConfiguration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Saving items through the API: the embedding follows, and a failing embedding never breaks the save. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "partsflow.ai.api-key=fake-key")
class ItemEmbeddingTriggerTest {

	private static final AtomicInteger COUNTER = new AtomicInteger();

	@Autowired
	MockMvc mvc;
	@Autowired
	FakeEmbeddingModel model;
	@Autowired
	ItemEmbeddingRepository repository;
	@Autowired
	EmbeddingGateway gateway;
	@Autowired
	ItemEmbeddingIndexer indexer;

	@BeforeEach
	void resetFake() {
		model.reset();
		indexer.resume(); // an earlier test's failure paused the indexer
	}

	private long create(String description) throws Exception {
		MvcResult result = mvc.perform(post("/api/items").contentType(MediaType.APPLICATION_JSON).content("""
				{"code": "TRG-%d", "description": "%s", "unit": "PZ", "reorderThreshold": 0}"""
				.formatted(COUNTER.incrementAndGet(), description))).andExpect(status().isCreated()).andReturn();
		String location = result.getResponse().getHeader("Location");
		return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
	}

	private boolean isUpToDate(long itemId) {
		ItemEmbeddingRepository.ItemSource source = repository.findItemSources(List.of(itemId)).getFirst();
		return !source.isStale(gateway.modelName(), gateway.dimensions(), source.sourceText().hash());
	}

	@Test
	void aNewItemIsEmbeddedAfterTheSave() throws Exception {
		long id = create("Filtro aria abitacolo");

		assertThat(model.batchSizes()).containsExactly(1);
		assertThat(isUpToDate(id)).isTrue();
	}

	@Test
	void updatingTheDescriptionEmbedsTheItemAgain() throws Exception {
		long id = create("Specchietto retrovisore");
		model.reset();

		mvc.perform(put("/api/items/" + id).contentType(MediaType.APPLICATION_JSON).content("""
				{"code": "TRG-CHANGED-%d", "description": "Specchietto retrovisore sinistro elettrico", "unit": "PZ", "reorderThreshold": 0}"""
				.formatted(id))).andExpect(status().isOk());

		assertThat(model.batchSizes()).containsExactly(1);
	}

	@Test
	void savingAnItemWorksEvenWhenTheEmbeddingServiceFails() throws Exception {
		model.failWith(() -> new ServerException(503, "UNAVAILABLE", "This model is currently experiencing high demand."));

		long id = create("Tergicristallo posteriore");

		mvc.perform(get("/api/items/" + id)).andExpect(status().isOk());
		assertThat(isUpToDate(id)).isFalse(); // pending: the next run will embed it
	}

	@Test
	void savingAnItemWorksEvenWhenTheEmbeddingCodeThrowsSomethingUnexpected() throws Exception {
		model.failWith(() -> new IllegalStateException("a bug in the embedding code"));

		long id = create("Lampadina H7");

		mvc.perform(get("/api/items/" + id)).andExpect(status().isOk());
	}

	@Test
	void theStatusEndpointCountsIndexedAndPendingItems() throws Exception {
		model.failWith(() -> new ServerException(503, "UNAVAILABLE", "high demand"));
		create("Clacson");
		model.reset();

		mvc.perform(get("/api/ai/embeddings/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.configured").value(true))
				.andExpect(jsonPath("$.model").value("gemini-embedding-001"))
				.andExpect(jsonPath("$.dimensions").value(768))
				.andExpect(jsonPath("$.dimensionsMatch").value(true))
				.andExpect(jsonPath("$.pending").isNumber());

		mvc.perform(post("/api/ai/embeddings/reindex"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.state").value("DONE"));
		mvc.perform(get("/api/ai/embeddings/status")).andExpect(jsonPath("$.pending").value(0));
	}
}
