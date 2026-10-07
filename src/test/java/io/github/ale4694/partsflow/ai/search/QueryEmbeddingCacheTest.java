package io.github.ale4694.partsflow.ai.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class QueryEmbeddingCacheTest {

	private static class MutableClock extends Clock {

		private Instant now = Instant.parse("2026-10-07T10:00:00Z");

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}

	@Test
	void theSameQueryWrittenDifferentlyIsOneEntry() {
		assertThat(QueryEmbeddingCache.key("m", "  Filtro   OLIO ")).isEqualTo(QueryEmbeddingCache.key("m", "filtro olio"));
		assertThat(QueryEmbeddingCache.key("m1", "filtro")).isNotEqualTo(QueryEmbeddingCache.key("m2", "filtro"));
	}

	@Test
	void remembersAVectorUntilItExpires() {
		MutableClock clock = new MutableClock();
		QueryEmbeddingCache cache = new QueryEmbeddingCache(10, Duration.ofMinutes(10), clock);
		float[] vector = { 1, 2, 3 };

		cache.put("k", vector);

		assertThat(cache.get("k")).containsSame(vector);
		clock.advance(Duration.ofMinutes(9));
		assertThat(cache.get("k")).isPresent();
		clock.advance(Duration.ofMinutes(2));
		assertThat(cache.get("k")).isEmpty();
	}

	@Test
	void keepsOnlyTheMostRecentlyUsedEntries() {
		QueryEmbeddingCache cache = new QueryEmbeddingCache(2, Duration.ofMinutes(10), new MutableClock());

		cache.put("a", new float[] { 1 });
		cache.put("b", new float[] { 2 });
		cache.get("a"); // a is now more recent than b
		cache.put("c", new float[] { 3 });

		assertThat(cache.get("a")).isPresent();
		assertThat(cache.get("b")).isEmpty(); // dropped: least recently used
		assertThat(cache.get("c")).isPresent();
	}
}
