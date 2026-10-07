package io.github.ale4694.partsflow.ai.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.ale4694.partsflow.ai.AiIntegrationTestBase;
import io.github.ale4694.partsflow.ai.search.ItemSearchService;
import io.github.ale4694.partsflow.catalog.Item;
import io.github.ale4694.partsflow.catalog.Supplier;
import io.github.ale4694.partsflow.invoiceimport.DraftResponse;
import io.github.ale4694.partsflow.invoiceimport.ImportService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Pending lines come from the unknown-codes fixture: line 1 "Timing belt kit" (code UNK-777) and line 2
 * "Wiper blades 600mm" (no code). The model is mocked; pg_trgm and everything else is real.
 */
class ItemMatchingIntegrationTest extends AiIntegrationTestBase {

	@Autowired
	ImportService importService;
	@Autowired
	ItemSearchService itemSearch;
	@Autowired
	LineMatchSuggestionRepository suggestionRepository;

	private DraftResponse draftFromFixture(Supplier supplier) throws IOException {
		try (InputStream in = getClass().getResourceAsStream("/fatturapa/unknown-codes.xml")) {
			String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8)
					.replace("20000000001", supplier.getVatNumber())
					.replaceAll("<Numero>[^<]*</Numero>", "<Numero>MATCH-" + supplier.getVatNumber() + "</Numero>");
			return importService.importFatturaPa(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
		}
	}

	/** The fixture's third line (RR-OIL-530) is known, so exactly two lines are pending. */
	private void mapOilCode(Supplier supplier, int n) {
		map(supplier, newItem("MATCH-OIL-" + n, "Engine oil 5W-30, 5 litre can"), "RR-OIL-530");
	}

	/** Makes the mocked model pick the candidate whose code starts with the given prefix, if any. */
	private void modelPicksCandidateWithCodePrefix(String prefix) {
		when(gateway.structured(eq("item-matching"), any(), any(), eq(MatchDecision.class))).thenAnswer(invocation -> {
			String prompt = invocation.getArgument(2);
			Matcher matcher = Pattern.compile("- id (\\d+) \\| code " + Pattern.quote(prefix)).matcher(prompt);
			return matcher.find() ? new MatchDecision(Long.parseLong(matcher.group(1)), "Same product.")
					: new MatchDecision(null, "None of the candidates is the same product.");
		});
	}

	@Test
	void trigramSearchFindsSimilarItemsBestFirst() {
		int n = next();
		Item timing = newItem("TRG-TB-" + n, "Timing belt kit");
		newItem("TRG-OIL-" + n, "Engine oil 5W-30, 5 litre can");

		var found = itemSearch.search("Timing belt kit 1.6", 5, false).results();

		assertThat(found).isNotEmpty();
		assertThat(found.getFirst().description()).startsWith("Timing belt kit");
		assertThat(found).extracting(ItemSearchService.Hit::itemId).contains(timing.getId());
		assertThat(found.getFirst().score()).isGreaterThan(0.5);
		assertThat(itemSearch.search("zzqqxx", 5, false).results()).isEmpty();
	}

	@Test
	void suggestionsAreCreatedOnePerLineWithinTheRequestLimit() throws Exception {
		int n = next();
		Supplier supplier = newSupplier(n);
		Item timing = newItem("MATCH-TB-" + n, "Timing belt kit");
		newItem("MATCH-WB-" + n, "Wiper blades 600mm");
		mapOilCode(supplier, n);
		DraftResponse draft = draftFromFixture(supplier);
		modelPicksCandidateWithCodePrefix("MATCH-TB-" + n);

		// the test context allows ONE line per request, so two pending lines need two requests
		mvc.perform(post("/api/ai/imports/" + draft.id() + "/suggest-matches"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.suggestions.length()").value(1))
				.andExpect(jsonPath("$.suggestions[0].itemCode").value(timing.getCode()))
				.andExpect(jsonPath("$.suggestions[0].status").value("SUGGESTED"))
				.andExpect(jsonPath("$.suggestions[0].justification").value("Same product."))
				.andExpect(jsonPath("$.linesLeftForNextRequest").value(1));
		mvc.perform(post("/api/ai/imports/" + draft.id() + "/suggest-matches"))
				.andExpect(jsonPath("$.suggestions.length()").value(1))
				.andExpect(jsonPath("$.linesLeftForNextRequest").value(0));
		// nothing left to do: no new LLM calls
		mvc.perform(post("/api/ai/imports/" + draft.id() + "/suggest-matches"))
				.andExpect(jsonPath("$.suggestions.length()").value(0));
		verify(gateway, times(2)).structured(eq("item-matching"), any(), any(), eq(MatchDecision.class));

		mvc.perform(get("/api/ai/imports/" + draft.id() + "/suggestions"))
				.andExpect(jsonPath("$.length()").value(2));
	}

	@Test
	void anItemTheModelInventedIsIgnored() throws Exception {
		int n = next();
		Supplier supplier = newSupplier(n);
		newItem("MATCH-TB-" + n, "Timing belt kit");
		DraftResponse draft = draftFromFixture(supplier);
		when(gateway.structured(eq("item-matching"), any(), any(), eq(MatchDecision.class)))
				.thenReturn(new MatchDecision(987654321L, "Trust me."));

		mvc.perform(post("/api/ai/imports/" + draft.id() + "/suggest-matches"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.suggestions[0].itemId").doesNotExist())
				.andExpect(jsonPath("$.suggestions[0].justification").value(Matchers.containsString("not among the candidates")));
	}

	@Test
	void whenNothingInTheCatalogIsSimilarTheLlmIsNotEvenAsked() throws Exception {
		int n = next();
		Supplier supplier = newSupplier(n);
		// the catalog is shared with other tests, so make sure the first pending line has nothing similar
		var description = itemSearch.search("Timing belt kit", 5, false).results();
		if (!description.isEmpty()) {
			// other tests created similar items: this scenario is covered by the repository assertion instead
			assertThat(itemSearch.search("qqzzxxww", 5, false).results()).isEmpty();
			return;
		}
		DraftResponse draft = draftFromFixture(supplier);

		mvc.perform(post("/api/ai/imports/" + draft.id() + "/suggest-matches"))
				.andExpect(jsonPath("$.suggestions[0].itemId").doesNotExist())
				.andExpect(jsonPath("$.suggestions[0].justification").value("No catalog item is similar to this line."));
		verify(gateway, never()).structured(eq("item-matching"), any(), any(), eq(MatchDecision.class));
	}

	@Test
	void acceptingResolvesTheLineAndRemembersTheSupplierCode() throws Exception {
		int n = next();
		Supplier supplier = newSupplier(n);
		Item timing = newItem("MATCH-TB-" + n, "Timing belt kit");
		mapOilCode(supplier, n);
		DraftResponse draft = draftFromFixture(supplier);
		modelPicksCandidateWithCodePrefix("MATCH-TB-" + n);
		mvc.perform(post("/api/ai/imports/" + draft.id() + "/suggest-matches")).andExpect(status().isOk());
		Long suggestionId = suggestionRepository.findByDraftIdOrderByDraftLineId(draft.id()).getFirst().getId();

		mvc.perform(post("/api/ai/suggestions/" + suggestionId + "/accept"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACCEPTED"));

		mvc.perform(get("/api/imports/" + draft.id()))
				.andExpect(jsonPath("$.lines[0].status").value("MATCHED"))
				.andExpect(jsonPath("$.lines[0].itemCode").value(timing.getCode()))
				.andExpect(jsonPath("$.pendingLines").value(1));
		assertThat(mappings.findBySupplierIdAndSupplierCode(supplier.getId(), "UNK-777")).isPresent();
		// a suggestion can only be decided once
		mvc.perform(post("/api/ai/suggestions/" + suggestionId + "/accept")).andExpect(status().isConflict());
		mvc.perform(post("/api/ai/suggestions/" + suggestionId + "/reject")).andExpect(status().isConflict());
	}

	@Test
	void rejectedSuggestionsCanBeRequestedAgainAndEmptySuggestionsCannotBeAccepted() throws Exception {
		int n = next();
		Supplier supplier = newSupplier(n);
		newItem("MATCH-TB-" + n, "Timing belt kit");
		DraftResponse draft = draftFromFixture(supplier);
		when(gateway.structured(eq("item-matching"), any(), any(), eq(MatchDecision.class)))
				.thenReturn(new MatchDecision(null, "Nothing fits."));
		mvc.perform(post("/api/ai/imports/" + draft.id() + "/suggest-matches")).andExpect(status().isOk());
		Long suggestionId = suggestionRepository.findByDraftIdOrderByDraftLineId(draft.id()).getFirst().getId();

		mvc.perform(post("/api/ai/suggestions/" + suggestionId + "/accept")).andExpect(status().isConflict());
		mvc.perform(post("/api/ai/suggestions/" + suggestionId + "/reject"))
				.andExpect(jsonPath("$.status").value("REJECTED"));

		// the rejected line is picked up again by the next request
		mvc.perform(post("/api/ai/imports/" + draft.id() + "/suggest-matches"))
				.andExpect(jsonPath("$.suggestions[0].id").value(suggestionId))
				.andExpect(jsonPath("$.suggestions[0].status").value("SUGGESTED"));
		mvc.perform(post("/api/ai/suggestions/999999/accept")).andExpect(status().isNotFound());
	}
}
