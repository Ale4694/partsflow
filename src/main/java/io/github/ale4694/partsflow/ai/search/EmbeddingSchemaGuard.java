package io.github.ale4694.partsflow.ai.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Checks that the vector column created by the migration has the size the application is configured for
 * (LLM_EMBEDDING_DIMENSIONS). The column size is fixed when Flyway runs V6, so changing the setting later does not
 * change the table. Instead of failing the whole application, a mismatch only switches the semantic search off:
 * indexing stops, searches use the text ranking alone, and the log and /api/ai/embeddings/status say why.
 * The fix (a new table with the right size, then a reindex) is described in ADR 0012.
 */
@Component
public class EmbeddingSchemaGuard {

	private static final Logger log = LoggerFactory.getLogger(EmbeddingSchemaGuard.class);

	private final ItemEmbeddingRepository repository;
	private final EmbeddingProperties properties;

	public EmbeddingSchemaGuard(ItemEmbeddingRepository repository, EmbeddingProperties properties) {
		this.repository = repository;
		this.properties = properties;
	}

	/** The size of the column in the database, or -1 when it cannot be read. */
	public int columnDimensions() {
		return repository.columnDimensions().orElse(-1);
	}

	/** True when the configured size equals the column size. */
	public boolean dimensionsMatch() {
		return columnDimensions() == properties.dimensions();
	}

	@EventListener(ApplicationReadyEvent.class)
	void logMismatchAtStartup() {
		if (!dimensionsMatch()) {
			log.warn("Semantic search is OFF: LLM_EMBEDDING_DIMENSIONS is {} but the database column item_embedding.embedding "
					+ "has {} dimensions. Searches fall back to text search. See ADR 0012 for how to reindex.",
					properties.dimensions(), columnDimensions());
		}
	}
}
