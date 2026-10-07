package io.github.ale4694.partsflow.ai.search;

import io.github.ale4694.partsflow.ai.AiProperties;
import io.github.ale4694.partsflow.ai.AiUnavailableException;
import io.github.ale4694.partsflow.ai.ProviderCallPolicy;
import io.github.ale4694.partsflow.ai.provider.EmbeddingOptionsFactory;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * The only place that asks the provider for embeddings, whichever provider is active (the twin of
 * {@code LlmGateway}, which does the same for chat). Every call goes through the {@link ProviderCallPolicy}, so
 * retries, quota errors and logging follow the same rules as chat, and nothing but the number of texts is logged.
 * <p>
 * Texts are sent in batches: one request carries up to {@code batchSize} texts (Gemini accepts 100), so indexing a
 * 150-item catalog costs two requests and embedding every pending line of an invoice costs one. The free tier
 * counts requests, not texts.
 */
@Service
public class EmbeddingGateway {

	private final ObjectProvider<EmbeddingModel> model;
	private final AiProperties ai;
	private final EmbeddingProperties properties;
	private final EmbeddingOptionsFactory options;
	private final ProviderCallPolicy policy;

	public EmbeddingGateway(ObjectProvider<EmbeddingModel> model, AiProperties ai, EmbeddingProperties properties,
			EmbeddingOptionsFactory options, ProviderCallPolicy policy) {
		this.model = model;
		this.ai = ai;
		this.properties = properties;
		this.options = options;
		this.policy = policy;
	}

	/** The embedding model in use: the configured one, or Gemini's default. Empty when there is none. */
	public String modelName() {
		if (!properties.model().isBlank()) {
			return properties.model().trim();
		}
		return ai.provider() == AiProperties.Provider.GEMINI ? EmbeddingProperties.GEMINI_DEFAULT_MODEL : "";
	}

	public int dimensions() {
		return properties.dimensions();
	}

	/** True when a key and an embedding model are configured. Incomplete settings mean text search only. */
	public boolean isConfigured() {
		return properties.enabled() && ai.configured() && !modelName().isBlank() && model.getIfAvailable() != null;
	}

	/** Embeds catalog texts (to be searched). One request per {@code batchSize} texts. */
	public List<float[]> embedDocuments(List<String> texts) {
		return embed("item-embedding", texts, options.forDocuments(modelName(), dimensions()));
	}

	/** Embeds search queries. One request per {@code batchSize} texts, so several queries share one request. */
	public List<float[]> embedQueries(List<String> queries) {
		return embed("query-embedding", queries, options.forQueries(modelName(), dimensions()));
	}

	private List<float[]> embed(String operation, List<String> texts, EmbeddingOptions requestOptions) {
		if (!isConfigured()) {
			throw new AiUnavailableException(AiUnavailableException.Reason.KEY_MISSING,
					"Semantic search is not configured: LLM_API_KEY and an embedding model are needed");
		}
		EmbeddingModel embeddingModel = model.getObject();
		List<float[]> vectors = new ArrayList<>(texts.size());
		for (int from = 0; from < texts.size(); from += properties.batchSize()) {
			List<String> batch = texts.subList(from, Math.min(texts.size(), from + properties.batchSize()));
			EmbeddingResponse response = policy.execute(operation, "texts=" + batch.size(),
					() -> embeddingModel.call(new EmbeddingRequest(batch, requestOptions)));
			List<Embedding> results = response.getResults();
			if (results.size() != batch.size()) {
				throw new AiUnavailableException(AiUnavailableException.Reason.REJECTED,
						"The embedding service returned " + results.size() + " vectors for " + batch.size() + " texts");
			}
			for (Embedding result : results) {
				vectors.add(checked(result.getOutput()));
			}
		}
		return vectors;
	}

	/** The vector must have the size of the database column; it is scaled to length 1 (see VectorMath). */
	private float[] checked(float[] vector) {
		if (vector.length != properties.dimensions()) {
			throw new AiUnavailableException(AiUnavailableException.Reason.REJECTED,
					"The embedding model returned " + vector.length + " numbers but " + properties.dimensions()
							+ " are expected (LLM_EMBEDDING_DIMENSIONS)");
		}
		return VectorMath.normalize(vector);
	}
}
