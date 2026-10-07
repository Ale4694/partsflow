package io.github.ale4694.partsflow.ai.provider;

import org.springframework.ai.embedding.EmbeddingOptions;

/**
 * The embedding options that depend on the provider. Gemini embeds differently for the texts to be found
 * (documents) and for the questions that look for them (queries); OpenAI-compatible services do not.
 * One implementation per provider, see {@code GeminiConfiguration} and {@code OpenAiCompatibleConfiguration}.
 */
public interface EmbeddingOptionsFactory {

	/** Options for embedding catalog texts that will be searched. */
	EmbeddingOptions forDocuments(String model, int dimensions);

	/** Options for embedding a search query. */
	EmbeddingOptions forQueries(String model, int dimensions);
}
