package io.github.ale4694.partsflow.ai.search;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Settings of the semantic search, under {@code partsflow.embeddings} in application.yml. */
@ConfigurationProperties("partsflow.embeddings")
public record EmbeddingProperties(
		/** false = never use embeddings (text search only), even if a key is configured. */
		@DefaultValue("true") boolean enabled,
		/** Embedding model (LLM_EMBEDDING_MODEL). Gemini has a default; an OpenAI-compatible service needs one. */
		@DefaultValue("") String model,
		/**
		 * Size of the vectors (LLM_EMBEDDING_DIMENSIONS). It must equal the size of the vector column created by
		 * the Flyway migration; a startup check compares them.
		 */
		@DefaultValue("768") int dimensions,
		/** Most texts sent in one embedding request (Gemini accepts up to 100). */
		@DefaultValue("100") int batchSize,
		@DefaultValue Indexing indexing,
		@DefaultValue Search search) {

	/** Gemini's embedding model, used when LLM_EMBEDDING_MODEL is not set and the provider is Gemini. */
	public static final String GEMINI_DEFAULT_MODEL = "gemini-embedding-001";

	public record Indexing(
			/** Run the background job that embeds items that are new, changed or whose embedding failed. */
			@DefaultValue("true") boolean scheduled,
			/** Embed an item right after it is saved, in the background. false = in the calling thread (tests). */
			@DefaultValue("true") boolean async,
			@DefaultValue("5m") Duration interval,
			@DefaultValue("15s") Duration initialDelay,
			/** After a quota or rate limit error the indexer stops for this long (or what the provider asked). */
			@DefaultValue("30m") Duration cooldown) {
	}

	public record Search(
			/** How many candidates each of the two rankings (vector, text) contributes to the fusion. */
			@DefaultValue("20") int poolSize,
			/** Constant of Reciprocal Rank Fusion: score = 1 / (rrfK + rank). 60 is the value of the original paper. */
			@DefaultValue("60") int rrfK,
			/** The text ranking ignores items less similar than this (pg_trgm similarity, 0 to 1). */
			@DefaultValue("0.1") double minTextSimilarity,
			/** The vector ranking ignores items less similar than this (cosine similarity, 0 to 1). */
			@DefaultValue("0.0") double minVectorSimilarity,
			@DefaultValue("200") int cacheSize,
			@DefaultValue("10m") Duration cacheTtl) {
	}
}
