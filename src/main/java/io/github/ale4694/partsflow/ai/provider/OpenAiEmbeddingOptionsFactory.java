package io.github.ale4694.partsflow.ai.provider;

import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;

/**
 * OpenAI-compatible services embed documents and queries the same way. The size is requested with the standard
 * {@code dimensions} parameter (supported by OpenAI's text-embedding-3 models; a model with a fixed size must be
 * configured with exactly that size, before the first migration).
 */
public class OpenAiEmbeddingOptionsFactory implements EmbeddingOptionsFactory {

	@Override
	public EmbeddingOptions forDocuments(String model, int dimensions) {
		return options(model, dimensions);
	}

	@Override
	public EmbeddingOptions forQueries(String model, int dimensions) {
		return options(model, dimensions);
	}

	private EmbeddingOptions options(String model, int dimensions) {
		return OpenAiEmbeddingOptions.builder().model(model).dimensions(dimensions).build();
	}
}
