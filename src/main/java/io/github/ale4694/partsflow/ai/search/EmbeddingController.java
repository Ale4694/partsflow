package io.github.ale4694.partsflow.ai.search;

import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** State of the semantic search index, and a way to run the indexer by hand. */
@RestController
@RequestMapping("/api/ai/embeddings")
public class EmbeddingController {

	/** @param pending items without an embedding of the current model and size (a changed item counts as indexed until the next run) */
	public record EmbeddingStatus(boolean configured, String model, int dimensions, int columnDimensions,
			boolean dimensionsMatch, long items, long indexed, long pending, Instant pausedUntil) {
	}

	private final EmbeddingGateway gateway;
	private final EmbeddingSchemaGuard guard;
	private final ItemEmbeddingRepository repository;
	private final ItemEmbeddingIndexer indexer;

	public EmbeddingController(EmbeddingGateway gateway, EmbeddingSchemaGuard guard, ItemEmbeddingRepository repository,
			ItemEmbeddingIndexer indexer) {
		this.gateway = gateway;
		this.guard = guard;
		this.repository = repository;
		this.indexer = indexer;
	}

	@GetMapping("/status")
	EmbeddingStatus status() {
		String model = gateway.modelName();
		ItemEmbeddingRepository.Counts counts = repository.counts(model, gateway.dimensions());
		return new EmbeddingStatus(gateway.isConfigured(), model, gateway.dimensions(), guard.columnDimensions(),
				guard.dimensionsMatch(), counts.items(), counts.indexed(), counts.items() - counts.indexed(),
				indexer.pausedUntil());
	}

	/**
	 * Embeds every item that needs it now (instead of waiting for the next scheduled run), even if the indexer is
	 * paused after a quota error: a person asking for it knows better than the timer.
	 */
	@PostMapping("/reindex")
	ItemEmbeddingIndexer.IndexReport reindex() {
		indexer.resume();
		return indexer.indexStale();
	}
}
