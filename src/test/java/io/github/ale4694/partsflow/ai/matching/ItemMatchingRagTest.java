package io.github.ale4694.partsflow.ai.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.ale4694.partsflow.ai.AiIntegrationTestBase;
import io.github.ale4694.partsflow.ai.search.FakeEmbeddingModel;
import io.github.ale4694.partsflow.ai.search.ItemEmbeddingIndexer;
import io.github.ale4694.partsflow.ai.search.ItemSearchService;
import io.github.ale4694.partsflow.catalog.Item;
import io.github.ale4694.partsflow.catalog.Supplier;
import io.github.ale4694.partsflow.invoiceimport.DraftResponse;
import io.github.ale4694.partsflow.invoiceimport.ImportService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * The retrieval step of the match suggestions (the "R" of RAG): the candidates shown to the LLM come from the
 * hybrid search, so an item described in other words is retrieved. Embeddings are ON here (the base class switches
 * them off), the LLM is mocked, and everything else is real. The fixture's pending lines are "Timing belt kit" and
 * "Wiper blades 600mm"; the catalog item below is its Italian equivalent and shares no word with it.
 */
@TestPropertySource(properties = { "partsflow.embeddings.enabled=true", "partsflow.ai.max-lines-per-match-request=10" })
class ItemMatchingRagTest extends AiIntegrationTestBase {

	@Autowired
	ImportService importService;
	@Autowired
	ItemSearchService itemSearch;
	@Autowired
	ItemEmbeddingIndexer indexer;
	@Autowired
	FakeEmbeddingModel model;

	private DraftResponse draftFromFixture(Supplier supplier) throws Exception {
		try (InputStream in = getClass().getResourceAsStream("/fatturapa/unknown-codes.xml")) {
			String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8)
					.replace("20000000001", supplier.getVatNumber())
					.replaceAll("<Numero>[^<]*</Numero>", "<Numero>RAG-" + supplier.getVatNumber() + "</Numero>");
			return importService.importFatturaPa(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
		}
	}

	@Test
	void candidatesAreRetrievedByMeaningAndAllLinesShareOneEmbeddingRequest() throws Exception {
		int n = next();
		Supplier supplier = newSupplier(n);
		Item belt = newItem("RAG-TB-" + n, "Cinghia distribuzione con tendicinghia"); // no word of "Timing belt kit"
		map(supplier, newItem("RAG-OIL-" + n, "Engine oil 5W-30, 5 litre can"), "RR-OIL-530");
		indexer.indexStale();
		indexer.resume();
		model.reset();
		DraftResponse draft = draftFromFixture(supplier);
		List<String> prompts = new ArrayList<>();
		when(gateway.structured(eq("item-matching"), any(), any(), eq(MatchDecision.class))).thenAnswer(invocation -> {
			String prompt = invocation.getArgument(2);
			prompts.add(prompt);
			Matcher candidate = Pattern.compile("- id (\\d+) \\| code RAG-TB-" + n).matcher(prompt);
			return candidate.find() ? new MatchDecision(Long.parseLong(candidate.group(1)), "Same product.")
					: new MatchDecision(null, "None of the candidates is the same product.");
		});

		// spelling alone cannot retrieve it...
		assertThat(itemSearch.search("Timing belt kit", 5, false).results())
				.extracting(ItemSearchService.Hit::itemId).doesNotContain(belt.getId());

		// ...the retrieval step of the suggestions can
		mvc.perform(post("/api/ai/imports/" + draft.id() + "/suggest-matches"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.suggestions[0].itemCode").value(belt.getCode()))
				.andExpect(jsonPath("$.suggestions[0].justification").value("Same product."));
		assertThat(prompts.getFirst()).contains("RAG-TB-" + n).contains("Cinghia distribuzione");
		// two pending lines, ONE embedding request
		assertThat(model.batchSizes()).containsExactly(2);
	}

	@Test
	void whenEmbeddingsFailTheSuggestionsStillWorkFromTextSearch() throws Exception {
		int n = next();
		Supplier supplier = newSupplier(n);
		Item timing = newItem("RAG-TXT-" + n, "Timing belt kit");
		map(supplier, newItem("RAG-OIL-" + n, "Engine oil 5W-30, 5 litre can"), "RR-OIL-530");
		indexer.indexStale();
		indexer.resume();
		model.failWith(() -> new com.google.genai.errors.ClientException(429, "RESOURCE_EXHAUSTED",
				"Quota exceeded. Please retry in 9h."));
		DraftResponse draft = draftFromFixture(supplier);
		when(gateway.structured(eq("item-matching"), any(), any(), eq(MatchDecision.class)))
				.thenAnswer(invocation -> {
					String prompt = invocation.getArgument(2);
					Matcher candidate = Pattern.compile("- id (\\d+) \\| code RAG-TXT-" + n).matcher(prompt);
					return candidate.find() ? new MatchDecision(Long.parseLong(candidate.group(1)), "Same product.")
							: new MatchDecision(null, "None.");
				});

		mvc.perform(post("/api/ai/imports/" + draft.id() + "/suggest-matches"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.suggestions[0].itemCode").value(timing.getCode()));
		model.reset();
	}
}
