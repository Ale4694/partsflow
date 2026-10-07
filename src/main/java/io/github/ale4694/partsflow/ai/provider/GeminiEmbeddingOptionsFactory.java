package io.github.ale4694.partsflow.ai.provider;

import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions.TaskType;

/**
 * Gemini embeddings take a task type: RETRIEVAL_DOCUMENT for the texts that are searched and RETRIEVAL_QUERY for the
 * questions, which makes a short question and a long description land close together. The requested size
 * ({@code dimensions}, 768) is Google's documented reduction of the 3072 numbers the model computes.
 */
public class GeminiEmbeddingOptionsFactory implements EmbeddingOptionsFactory {

	@Override
	public EmbeddingOptions forDocuments(String model, int dimensions) {
		return options(model, dimensions, TaskType.RETRIEVAL_DOCUMENT);
	}

	@Override
	public EmbeddingOptions forQueries(String model, int dimensions) {
		return options(model, dimensions, TaskType.RETRIEVAL_QUERY);
	}

	private EmbeddingOptions options(String model, int dimensions, TaskType taskType) {
		return GoogleGenAiTextEmbeddingOptions.builder().model(model).dimensions(dimensions).taskType(taskType).build();
	}
}
