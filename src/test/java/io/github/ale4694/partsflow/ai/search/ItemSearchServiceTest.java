package io.github.ale4694.partsflow.ai.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.errors.ClientException;
import io.github.ale4694.partsflow.TestcontainersConfiguration;
import io.github.ale4694.partsflow.ai.search.ItemSearchService.FallbackReason;
import io.github.ale4694.partsflow.ai.search.ItemSearchService.Hit;
import io.github.ale4694.partsflow.ai.search.ItemSearchService.Mode;
import io.github.ale4694.partsflow.ai.search.ItemSearchService.SearchResult;
import io.github.ale4694.partsflow.catalog.Item;
import io.github.ale4694.partsflow.catalog.ItemRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Hybrid search against a real database with the fake embedding model. The model name makes this test its own
 * Spring context, so its own empty database: only the items created here exist.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = { "partsflow.ai.api-key=fake-key", "partsflow.embeddings.model=search-test-model" })
class ItemSearchServiceTest {

	@Autowired
	ItemSearchService service;
	@Autowired
	ItemEmbeddingIndexer indexer;
	@Autowired
	ItemRepository items;
	@Autowired
	FakeEmbeddingModel model;

	private Map<String, Long> ids;

	/** Creates the catalog once and embeds it (the database lives as long as this test's context). */
	@BeforeEach
	void catalog() {
		if (items.count() == 0) {
			ids = Map.of(
					"cartuccia", create("AAA-1", "Cartuccia lubrificante motore 1.2 FIRE"),
					"pastiglie", create("AAA-2", "Pastiglie freni anteriori Fiat Panda"),
					"filtroAria", create("AAA-3", "Filtro aria abitacolo"),
					"tergi", create("AAA-4", "Tergicristallo anteriore 600 mm"),
					"olio", create("AAA-5", "Olio motore 5W30 sintetico 5 litri"));
			indexer.indexStale();
		}
		else {
			ids = Map.of(
					"cartuccia", idOf("AAA-1"), "pastiglie", idOf("AAA-2"), "filtroAria", idOf("AAA-3"),
					"tergi", idOf("AAA-4"), "olio", idOf("AAA-5"));
		}
		model.reset();
		indexer.resume();
	}

	private long create(String code, String description) {
		return items.save(new Item(code, description, "PZ", BigDecimal.ZERO)).getId();
	}

	private long idOf(String code) {
		return items.findByCode(code).orElseThrow().getId();
	}

	private static List<Long> idsOf(SearchResult result) {
		return result.results().stream().map(Hit::itemId).toList();
	}

	@Test
	void meaningFindsAnItemThatSharesNoWordsWithTheQuery() {
		SearchResult result = service.search("filtro olio Fiat Panda", 5, true);

		assertThat(result.mode()).isEqualTo(Mode.HYBRID);
		assertThat(result.fallbackReason()).isNull();
		Hit cartuccia = hit(result, ids.get("cartuccia")); // "cartuccia lubrificante ... FIRE": no word in common
		assertThat(cartuccia.textScore()).isNull(); // spelling alone does not find it...
		assertThat(cartuccia.vectorScore()).isGreaterThan(0.4); // ...meaning does
		assertThat(cartuccia.score()).isBetween(0.0, 1.0);
		// it is the closest by meaning...
		assertThat(result.results().stream().mapToDouble(h -> h.vectorScore() == null ? 0 : h.vectorScore()).max())
				.hasValue(cartuccia.vectorScore());
		// ...and it is among the results. It is not necessarily FIRST: Reciprocal Rank Fusion adds the two rankings,
		// so items that also match a word ("Fiat Panda", "filtro") get a bonus (see ADR 0012).
		assertThat(idsOf(result)).contains(ids.get("cartuccia"));
	}

	private static Hit hit(SearchResult result, long itemId) {
		return result.results().stream().filter(h -> h.itemId() == itemId).findFirst().orElseThrow();
	}

	@Test
	void spellingAloneDoesNotFindIt() {
		SearchResult result = service.search("filtro olio Fiat Panda", 5, false);

		assertThat(result.mode()).isEqualTo(Mode.TEXT);
		assertThat(result.fallbackReason()).isNull(); // text was asked for, nothing "fell back"
		assertThat(idsOf(result)).doesNotContain(ids.get("cartuccia"));
		assertThat(model.batchSizes()).isEmpty(); // no embedding request for a text search
	}

	@Test
	void anItemFoundByBothRankingsBeatsOnesFoundByOne() {
		SearchResult result = service.search("olio motore 5W30", 5, true);

		Hit best = result.results().getFirst();
		assertThat(best.itemId()).isEqualTo(ids.get("olio"));
		assertThat(best.vectorScore()).isNotNull();
		assertThat(best.textScore()).isNotNull();
		assertThat(best.score()).isEqualTo(1.0); // first in both rankings = the best possible fused score
		assertThat(result.results().stream().skip(1).mapToDouble(Hit::score)).allMatch(score -> score < 1.0);
	}

	@Test
	void resultsComeSortedByScoreAndCarryTheStock() {
		SearchResult result = service.search("freni Panda", 5, true);

		List<Double> scores = result.results().stream().map(Hit::score).toList();
		assertThat(scores).isSortedAccordingTo(java.util.Comparator.reverseOrder());
		assertThat(result.results().getFirst().itemId()).isEqualTo(ids.get("pastiglie"));
		assertThat(result.results().getFirst().quantity()).isEqualByComparingTo("0");
	}

	@Test
	void aSearchCostsOneEmbeddingRequestAndARepeatedQueryCostsNone() {
		service.search("tergicristallo", 5, true);
		service.search("  TERGICRISTALLO ", 5, true);
		service.search("tergicristallo", 3, true);

		assertThat(model.batchSizes()).containsExactly(1);
	}

	@Test
	void severalQueriesShareOneEmbeddingRequest() {
		List<SearchResult> results = service.searchAll(
				List.of("batteria avviamento", "specchietto retrovisore", "candela accensione"), 5, true);

		assertThat(results).hasSize(3).allSatisfy(result -> assertThat(result.mode()).isEqualTo(Mode.HYBRID));
		assertThat(model.batchSizes()).containsExactly(3);
		model.reset();
		service.searchAll(List.of("batteria avviamento", "specchietto retrovisore", "cinghia servizi"), 5, true);
		assertThat(model.batchSizes()).containsExactly(1); // only the new query is sent
	}

	@Test
	void whenTheProviderFailsTheSearchFallsBackToTextAndSaysWhy() {
		model.failWith(() -> new ClientException(429, "RESOURCE_EXHAUSTED",
				"Quota exceeded for metric: embed_content_free_tier_requests. Please retry in 9h3m1.2s."));

		SearchResult result = service.search("filtro aria abitacolo", 5, true);

		assertThat(result.mode()).isEqualTo(Mode.TEXT);
		assertThat(result.fallbackReason()).isEqualTo(FallbackReason.PROVIDER_ERROR);
		assertThat(result.results().getFirst().itemId()).isEqualTo(ids.get("filtroAria")); // text still works
		assertThat(result.results().getFirst().vectorScore()).isNull();
	}

	@Test
	void anEmptyQueryReturnsNothingAndSendsNothing() {
		SearchResult result = service.search("   ", 5, true);

		assertThat(result.results()).isEmpty();
		assertThat(model.batchSizes()).isEmpty();
	}
}
