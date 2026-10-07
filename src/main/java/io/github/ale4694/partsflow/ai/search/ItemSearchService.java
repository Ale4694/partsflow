package io.github.ale4694.partsflow.ai.search;

import io.github.ale4694.partsflow.ai.AiUnavailableException;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Finds catalog items for a piece of text, by meaning AND by spelling (hybrid search), and falls back to spelling
 * alone whenever the meaning side is not available. The steps of a hybrid search:
 * <ol>
 *   <li>turn the query into an embedding (one provider request, or none if the same query is in the cache);</li>
 *   <li>rank the items by cosine similarity to it, and by pg_trgm similarity of their text (two rankings);</li>
 *   <li>fuse the rankings with Reciprocal Rank Fusion (see {@link ItemSearchRepository}).</li>
 * </ol>
 * The answer always says which mode produced it, and why it fell back.
 */
@Service
public class ItemSearchService {

	private static final Logger log = LoggerFactory.getLogger(ItemSearchService.class);

	public enum Mode {
		/** Meaning (embeddings) and spelling (pg_trgm) together. */
		HYBRID,
		/** Spelling only. */
		TEXT
	}

	/** Why a search that wanted the meaning side ran as TEXT. */
	public enum FallbackReason {
		/** No API key or embedding model configured. */
		NOT_CONFIGURED,
		/** LLM_EMBEDDING_DIMENSIONS differs from the database column. */
		DIMENSION_MISMATCH,
		/** No item has an embedding of the current model yet. */
		NOT_INDEXED,
		/** The provider could not embed the query (no quota, overloaded, rejected...). */
		PROVIDER_ERROR
	}

	/**
	 * @param score 0 to 1: for HYBRID the fused RRF score relative to the best possible one (first in both rankings),
	 *        for TEXT the pg_trgm similarity
	 * @param vectorScore cosine similarity, null if the item was not among the nearest vectors (or in TEXT mode)
	 * @param textScore pg_trgm similarity, null if the item was not among the textual matches
	 */
	public record Hit(long itemId, String code, String description, String unit, BigDecimal reorderThreshold,
			BigDecimal quantity, double score, Double vectorScore, Double textScore) {
	}

	/** @param fallbackReason null unless a hybrid search had to run as TEXT */
	public record SearchResult(Mode mode, FallbackReason fallbackReason, List<Hit> results) {
	}

	private final ItemSearchRepository search;
	private final ItemEmbeddingRepository embeddings;
	private final EmbeddingGateway gateway;
	private final EmbeddingSchemaGuard guard;
	private final EmbeddingProperties properties;
	private final QueryEmbeddingCache cache;

	public ItemSearchService(ItemSearchRepository search, ItemEmbeddingRepository embeddings, EmbeddingGateway gateway,
			EmbeddingSchemaGuard guard, EmbeddingProperties properties, Clock clock) {
		this.search = search;
		this.embeddings = embeddings;
		this.gateway = gateway;
		this.guard = guard;
		this.properties = properties;
		this.cache = new QueryEmbeddingCache(properties.search().cacheSize(), properties.search().cacheTtl(), clock);
	}

	/** Searches with the meaning side when it is available ({@code semantic}), else by spelling. */
	public SearchResult search(String query, int limit, boolean semantic) {
		return searchAll(List.of(query), limit, semantic).getFirst();
	}

	/**
	 * Several searches at once (the pending lines of an invoice): all their queries that are not cached share ONE
	 * embedding request.
	 */
	public List<SearchResult> searchAll(List<String> queries, int limit, boolean semantic) {
		if (!semantic) {
			return queries.stream().map(query -> textResult(query, limit, null)).toList();
		}
		FallbackReason unavailable = whyUnavailable();
		if (unavailable != null) {
			return queries.stream().map(query -> textResult(query, limit, unavailable)).toList();
		}
		// a blank query needs no embedding (and no request): only the others are embedded
		List<String> toEmbed = queries.stream().filter(query -> !query.isBlank()).toList();
		List<float[]> embedded;
		try {
			embedded = toEmbed.isEmpty() ? List.of() : queryVectors(toEmbed);
		}
		catch (AiUnavailableException ex) {
			// the query text is never logged, only why the provider could not help
			log.warn("Semantic search fell back to text search: {}", ex.reason().code());
			return queries.stream().map(query -> textResult(query, limit, FallbackReason.PROVIDER_ERROR)).toList();
		}
		List<SearchResult> results = new ArrayList<>();
		int next = 0;
		for (String query : queries) {
			results.add(query.isBlank() ? new SearchResult(Mode.HYBRID, null, List.of())
					: hybridResult(query, embedded.get(next++), limit));
		}
		return results;
	}

	private FallbackReason whyUnavailable() {
		if (!gateway.isConfigured()) {
			return FallbackReason.NOT_CONFIGURED;
		}
		if (!guard.dimensionsMatch()) {
			return FallbackReason.DIMENSION_MISMATCH;
		}
		if (embeddings.counts(gateway.modelName(), gateway.dimensions()).indexed() == 0) {
			return FallbackReason.NOT_INDEXED;
		}
		return null;
	}

	/** The embedding of each query: from the cache, or from one shared provider request. */
	private List<float[]> queryVectors(List<String> queries) {
		String model = gateway.modelName();
		float[][] vectors = new float[queries.size()][];
		List<Integer> missing = new ArrayList<>();
		for (int i = 0; i < queries.size(); i++) {
			Optional<float[]> cached = cache.get(QueryEmbeddingCache.key(model, queries.get(i)));
			if (cached.isPresent()) {
				vectors[i] = cached.get();
			}
			else {
				missing.add(i);
			}
		}
		if (!missing.isEmpty()) {
			List<float[]> fresh = gateway.embedQueries(missing.stream().map(queries::get).toList());
			for (int j = 0; j < missing.size(); j++) {
				vectors[missing.get(j)] = fresh.get(j);
				cache.put(QueryEmbeddingCache.key(model, queries.get(missing.get(j))), fresh.get(j));
			}
		}
		return List.of(vectors);
	}

	private SearchResult textResult(String query, int limit, FallbackReason reason) {
		if (query.isBlank()) {
			return new SearchResult(Mode.TEXT, reason, List.of());
		}
		List<Hit> hits = search.textOnly(query.trim(), properties.search().minTextSimilarity(), limit).stream()
				.map(row -> new Hit(row.itemId(), row.code(), row.description(), row.unit(), row.reorderThreshold(),
						row.quantity(), row.fusedScore(), null, row.textSimilarity()))
				.toList();
		return new SearchResult(Mode.TEXT, reason, hits);
	}

	private SearchResult hybridResult(String query, float[] vector, int limit) {
		EmbeddingProperties.Search settings = properties.search();
		// the best possible fused score: first in both rankings, 2 / (k + 1). It scales every score to 0..1.
		double best = 2.0 / (settings.rrfK() + 1);
		List<Hit> hits = search.hybrid(query.trim(), vector, gateway.modelName(), gateway.dimensions(),
				settings.poolSize(), settings.rrfK(), settings.minTextSimilarity(), settings.minVectorSimilarity(), limit)
				.stream()
				.map(row -> new Hit(row.itemId(), row.code(), row.description(), row.unit(), row.reorderThreshold(),
						row.quantity(), Math.min(1.0, row.fusedScore() / best), row.vectorSimilarity(), row.textSimilarity()))
				.toList();
		return new SearchResult(Mode.HYBRID, null, hits);
	}
}
