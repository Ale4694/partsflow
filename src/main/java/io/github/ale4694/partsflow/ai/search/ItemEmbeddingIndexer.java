package io.github.ale4694.partsflow.ai.search;

import io.github.ale4694.partsflow.ai.AiUnavailableException;
import io.github.ale4694.partsflow.ai.search.ItemEmbeddingRepository.ItemSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Keeps the item embeddings up to date. The rule is simple and the same everywhere it runs:
 * <ol>
 *   <li>read every item with its current text (code, description, supplier codes) and its stored embedding;</li>
 *   <li>an item is <b>stale</b> when it has no embedding, when the SHA-256 of its text differs from the stored hash
 *       (it changed), or when the stored model or size differs from the configured one (the model was changed);</li>
 *   <li>embed the stale items in batches (one provider request per batch) and store the vectors.</li>
 * </ol>
 * Items whose text did not change cost nothing. Nothing here runs inside the transaction that saves an item: the
 * item is saved first, and an embedding that fails (no key, quota, provider down) simply leaves the item stale,
 * to be picked up by the next run. After a quota error the indexer pauses instead of asking again and again.
 */
@Service
public class ItemEmbeddingIndexer {

	private static final Logger log = LoggerFactory.getLogger(ItemEmbeddingIndexer.class);

	public enum State {
		/** Everything that needed it was embedded (possibly nothing). */
		DONE,
		/** The embedding failed part-way; the rest stays stale and is retried later. */
		FAILED,
		/** No key or embedding model configured. */
		NOT_CONFIGURED,
		/** LLM_EMBEDDING_DIMENSIONS differs from the database column (see EmbeddingSchemaGuard). */
		DIMENSION_MISMATCH,
		/** A recent quota or rate limit error: not asking the provider again yet. */
		PAUSED
	}

	/** What a run did. {@code stale} counts the items that needed embedding when the run started. */
	public record IndexReport(State state, int stale, int embedded, int failed) {
	}

	private final ItemEmbeddingRepository repository;
	private final EmbeddingGateway gateway;
	private final EmbeddingSchemaGuard guard;
	private final EmbeddingProperties properties;
	private final Clock clock;

	private volatile Instant pausedUntil;

	public ItemEmbeddingIndexer(ItemEmbeddingRepository repository, EmbeddingGateway gateway,
			EmbeddingSchemaGuard guard, EmbeddingProperties properties, Clock clock) {
		this.repository = repository;
		this.gateway = gateway;
		this.guard = guard;
		this.properties = properties;
		this.clock = clock;
	}

	/** Embeds every stale item of the catalog. */
	public IndexReport indexStale() {
		return index(null);
	}

	/** Embeds the stale ones among the given items (after they were saved). */
	public IndexReport indexItems(Collection<Long> itemIds) {
		return index(itemIds);
	}

	/** Forgets a pause (a person asked for a run now, or the quota was renewed). */
	public void resume() {
		pausedUntil = null;
	}

	public Instant pausedUntil() {
		Instant until = pausedUntil;
		return until != null && until.isAfter(clock.instant()) ? until : null;
	}

	/** One run at a time: two runs would embed the same items twice and spend the quota twice. */
	private synchronized IndexReport index(Collection<Long> itemIds) {
		if (!gateway.isConfigured()) {
			return new IndexReport(State.NOT_CONFIGURED, 0, 0, 0);
		}
		if (!guard.dimensionsMatch()) {
			return new IndexReport(State.DIMENSION_MISMATCH, 0, 0, 0);
		}
		if (pausedUntil() != null) {
			return new IndexReport(State.PAUSED, 0, 0, 0);
		}

		String model = gateway.modelName();
		int dimensions = gateway.dimensions();
		List<ItemSource> stale = new ArrayList<>();
		for (ItemSource source : repository.findItemSources(itemIds)) {
			if (source.isStale(model, dimensions, source.sourceText().hash())) {
				stale.add(source);
			}
		}
		if (stale.isEmpty()) {
			return new IndexReport(State.DONE, 0, 0, 0);
		}

		int embedded = 0;
		for (int from = 0; from < stale.size(); from += properties.batchSize()) {
			List<ItemSource> batch = stale.subList(from, Math.min(stale.size(), from + properties.batchSize()));
			List<String> texts = batch.stream().map(source -> source.sourceText().text()).toList();
			try {
				List<float[]> vectors = gateway.embedDocuments(texts); // one provider request for the whole batch
				for (int i = 0; i < batch.size(); i++) {
					ItemSource source = batch.get(i);
					// the hash is that of the text just embedded: if the item changed meanwhile, the next run sees it
					repository.upsert(source.itemId(), vectors.get(i), model, dimensions, source.sourceText().hash(),
							clock.instant());
				}
				embedded += batch.size();
			}
			catch (AiUnavailableException ex) {
				pauseAfter(ex);
				int failed = stale.size() - embedded;
				log.warn("Embedding indexing stopped: embedded={} failed={} reason={}", embedded, failed,
						ex.reason().code());
				return new IndexReport(State.FAILED, stale.size(), embedded, failed);
			}
		}
		log.info("Embedding indexing done: embedded={} model={}", embedded, model);
		return new IndexReport(State.DONE, stale.size(), embedded, 0);
	}

	/** A quota or rate limit error pauses the indexer for what the provider asked, or the configured cooldown. */
	private void pauseAfter(AiUnavailableException ex) {
		Duration pause = switch (ex.reason()) {
			case DAILY_QUOTA_EXHAUSTED, RATE_LIMITED, REJECTED, KEY_MISSING ->
				ex.retryAfter() != null ? ex.retryAfter() : properties.indexing().cooldown();
			case TEMPORARILY_UNAVAILABLE -> properties.indexing().interval();
		};
		pausedUntil = clock.instant().plus(pause);
	}
}
