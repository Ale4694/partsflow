package io.github.ale4694.partsflow.ai.search;

import java.time.Duration;

/** Embedding settings for tests that do not start Spring. */
public final class TestEmbeddingProperties {

	private TestEmbeddingProperties() {
	}

	public static EmbeddingProperties of(String model, int dimensions, int batchSize) {
		return new EmbeddingProperties(true, model, dimensions, batchSize,
				new EmbeddingProperties.Indexing(false, false, Duration.ofMinutes(5), Duration.ofSeconds(1),
						Duration.ofMinutes(30)),
				new EmbeddingProperties.Search(20, 60, 0.1, 0.0, 200, Duration.ofMinutes(10)));
	}
}
