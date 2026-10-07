package io.github.ale4694.partsflow.ai.search;

import io.github.ale4694.partsflow.catalog.ItemChanged;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The two things that start the indexer, neither of which can block or break saving an item:
 * <ul>
 *   <li>after an item (or its supplier codes) was saved and the transaction COMMITTED, the item is embedded in the
 *       background (one worker thread, so runs queue up instead of overlapping);</li>
 *   <li>a scheduled job re-runs the indexer every few minutes: this is the retry for items whose embedding failed,
 *       and the way existing items get their first embedding after the first start.</li>
 * </ul>
 */
@Component
class ItemEmbeddingTrigger {

	private static final Logger log = LoggerFactory.getLogger(ItemEmbeddingTrigger.class);

	private final ItemEmbeddingIndexer indexer;
	private final EmbeddingProperties properties;
	private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "embedding-indexer");
		thread.setDaemon(true);
		return thread;
	});

	ItemEmbeddingTrigger(ItemEmbeddingIndexer indexer, EmbeddingProperties properties) {
		this.indexer = indexer;
		this.properties = properties;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	void onItemChanged(ItemChanged event) {
		Runnable job = () -> run(() -> indexer.indexItems(event.itemIds()));
		if (properties.indexing().async()) {
			worker.submit(job);
		}
		else {
			job.run(); // tests: no thread, the result is there when saving returns
		}
	}

	@Scheduled(initialDelayString = "${partsflow.embeddings.indexing.initial-delay:15s}",
			fixedDelayString = "${partsflow.embeddings.indexing.interval:5m}")
	void scheduledRun() {
		if (properties.indexing().scheduled()) {
			run(indexer::indexStale);
		}
	}

	/** An embedding problem is logged and forgotten here: it must never reach the code that saved the item. */
	private void run(java.util.function.Supplier<ItemEmbeddingIndexer.IndexReport> job) {
		try {
			job.get();
		}
		catch (RuntimeException ex) {
			log.warn("Embedding indexing failed unexpectedly: {}", ex.getClass().getSimpleName());
		}
	}

	@PreDestroy
	void stop() {
		worker.shutdownNow();
	}
}
