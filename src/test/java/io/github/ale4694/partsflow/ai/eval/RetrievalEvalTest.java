package io.github.ale4694.partsflow.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.ale4694.partsflow.PgvectorContainerConfiguration;
import io.github.ale4694.partsflow.ai.AiUnavailableException;
import io.github.ale4694.partsflow.ai.search.ItemEmbeddingIndexer;
import io.github.ale4694.partsflow.ai.search.ItemSearchService;
import io.github.ale4694.partsflow.ai.search.ItemSearchService.Hit;
import io.github.ale4694.partsflow.ai.search.ItemSearchService.Mode;
import io.github.ale4694.partsflow.ai.search.ItemSearchService.SearchResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Optional retrieval eval against the REAL embedding model. Strictly opt-in, like ExtractionEvalTest: it runs only
 * when LLM_API_KEY is set AND LLM_EVAL=true. A plain {@code ./mvnw verify} never calls the provider, even if the key
 * is exported in your shell, and agents never set LLM_EVAL.
 * <p>
 * It loads the demo catalog (profile "demo", about 125 items), lets the indexer embed it, then searches 20 queries
 * two ways and reports how often the expected item is in the top 5: by spelling only (text mode) and with the
 * hybrid search. It is cheap on purpose: the catalog is embedded in 2 batch requests and ALL queries are embedded
 * together in ONE request, so the whole eval costs 3 provider requests (the free tier allows about 1000 embedding
 * requests per day). No pauses are needed. It does not fail on a low score: the report is the point, so that a
 * change of model, dimension or fusion rule can be compared. Printed and written to target/retrieval-eval-report.txt.
 * <p>
 * Run it deliberately: {@code LLM_EVAL=true ./mvnw -Dtest=RetrievalEvalTest test} with LLM_API_KEY exported.
 */
@SpringBootTest
@ActiveProfiles("demo")
// the PostgreSQL container only: NOT the fake embedding model that every other test imports
@Import(PgvectorContainerConfiguration.class)
// the key is read from the environment, never written anywhere (the test default is an empty key)
@TestPropertySource(properties = "partsflow.ai.api-key=${LLM_API_KEY}")
// Both conditions must hold (JUnit ANDs repeated conditions): a key alone never triggers real calls
@EnabledIfEnvironmentVariable(named = "LLM_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "LLM_EVAL", matches = "true")
class RetrievalEvalTest {

	private static final int TOP = 5;

	@Autowired
	ItemSearchService search;
	@Autowired
	ItemEmbeddingIndexer indexer;

	/** 1-based rank of the first acceptable item in the results, or 0 when none of the top results is acceptable. */
	private static int rank(List<Hit> hits, RetrievalEvalQueries.Query query) {
		for (int i = 0; i < hits.size(); i++) {
			if (query.acceptable().contains(hits.get(i).code())) {
				return i + 1;
			}
		}
		return 0;
	}

	@Test
	void measureRetrievalQuality() throws Exception {
		// the demo loader already announced the catalog; this makes sure nothing is left (and costs nothing if done)
		indexer.resume();
		ItemEmbeddingIndexer.IndexReport indexing = indexer.indexStale();
		assumeTrue(indexing.state() == ItemEmbeddingIndexer.State.DONE,
				"The catalog could not be embedded (" + indexing.state() + "): key, model or quota problem");

		List<RetrievalEvalQueries.Query> queries = RetrievalEvalQueries.all();
		List<String> texts = queries.stream().map(RetrievalEvalQueries.Query::text).toList();
		List<SearchResult> text = search.searchAll(texts, TOP, false);
		List<SearchResult> hybrid;
		try {
			hybrid = search.searchAll(texts, TOP, true); // ONE embedding request for all the queries
		}
		catch (AiUnavailableException ex) {
			assumeTrue(false, "The embedding service was unavailable: " + ex.reason().code());
			return;
		}
		assumeTrue(hybrid.stream().allMatch(result -> result.mode() == Mode.HYBRID),
				"The search fell back to text: " + hybrid.getFirst().fallbackReason());

		StringBuilder report = new StringBuilder("Retrieval eval: ").append(queries.size()).append(" queries, top ")
				.append(TOP).append("\n\n");
		report.append(String.format("%-46s %-12s %6s %8s%n", "query", "expected", "text", "hybrid"));
		int textHits = 0;
		int hybridHits = 0;
		double textReciprocal = 0;
		double hybridReciprocal = 0;
		for (int i = 0; i < queries.size(); i++) {
			RetrievalEvalQueries.Query query = queries.get(i);
			int textRank = rank(text.get(i).results(), query);
			int hybridRank = rank(hybrid.get(i).results(), query);
			textHits += textRank > 0 ? 1 : 0;
			hybridHits += hybridRank > 0 ? 1 : 0;
			textReciprocal += textRank > 0 ? 1.0 / textRank : 0;
			hybridReciprocal += hybridRank > 0 ? 1.0 / hybridRank : 0;
			report.append(String.format("%-46s %-12s %6s %8s%n", query.text(),
					String.join("/", query.acceptable().stream().sorted().toList()),
					textRank > 0 ? "#" + textRank : "-", hybridRank > 0 ? "#" + hybridRank : "-"));
		}
		report.append(String.format(Locale.ROOT, "%nhit rate in the top %d: text only %d/%d (%.0f%%), hybrid %d/%d (%.0f%%)%n",
				TOP, textHits, queries.size(), 100.0 * textHits / queries.size(), hybridHits, queries.size(),
				100.0 * hybridHits / queries.size()));
		report.append(String.format(Locale.ROOT, "mean reciprocal rank: text only %.2f, hybrid %.2f%n",
				textReciprocal / queries.size(), hybridReciprocal / queries.size()));

		System.out.println(report);
		Files.writeString(Path.of("target", "retrieval-eval-report.txt"), report.toString());
		assertThat(hybridHits).isPositive();
	}
}
