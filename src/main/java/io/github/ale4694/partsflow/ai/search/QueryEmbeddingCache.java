package io.github.ale4694.partsflow.ai.search;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A small in-memory cache of query embeddings: the same question asked again (the user retypes it, two pending
 * lines have the same description, the assistant repeats a search) costs no provider request. It keeps the most
 * recently used {@code maxEntries} queries and forgets each after {@code ttl}. Queries are only ever held in memory
 * and never logged. Plain code instead of a cache library: it is 30 lines and easy to explain.
 */
class QueryEmbeddingCache {

	private record Entry(float[] vector, Instant expires) {
	}

	private final Map<String, Entry> entries;
	private final Duration ttl;
	private final Clock clock;

	QueryEmbeddingCache(int maxEntries, Duration ttl, Clock clock) {
		this.ttl = ttl;
		this.clock = clock;
		// access order = the least recently used entry is dropped first
		this.entries = new LinkedHashMap<>(16, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
				return size() > maxEntries;
			}
		};
	}

	/** "Filtro  OLIO" and "filtro olio" are the same question. The model name keeps vectors of models apart. */
	static String key(String model, String query) {
		return model + "|" + query.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	synchronized Optional<float[]> get(String key) {
		Entry entry = entries.get(key);
		if (entry == null) {
			return Optional.empty();
		}
		if (!entry.expires().isAfter(clock.instant())) {
			entries.remove(key);
			return Optional.empty();
		}
		return Optional.of(entry.vector());
	}

	synchronized void put(String key, float[] vector) {
		entries.put(key, new Entry(vector, clock.instant().plus(ttl)));
	}
}
