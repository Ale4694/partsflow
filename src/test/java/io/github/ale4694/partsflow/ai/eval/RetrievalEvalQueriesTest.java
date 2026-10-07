package io.github.ale4694.partsflow.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The retrieval eval only runs with a real key, so this checks (offline) that its query data is sound. */
class RetrievalEvalQueriesTest {

	@Test
	void everyExpectedItemExistsInTheDemoCatalogAndQueriesAreDistinct() throws Exception {
		Set<String> codes = new HashSet<>();
		try (InputStream in = new ClassPathResource("demo/catalog.json").getInputStream()) {
			JsonNode items = JsonMapper.builder().build().readTree(in).get("items");
			items.forEach(item -> codes.add(item.get("code").asString()));
		}

		Set<String> texts = new HashSet<>();
		for (RetrievalEvalQueries.Query query : RetrievalEvalQueries.all()) {
			assertThat(texts.add(query.text())).as("duplicate query " + query.text()).isTrue();
			assertThat(query.acceptable()).isNotEmpty();
			assertThat(codes).as("catalog contains the expected items of '" + query.text() + "'")
					.containsAll(query.acceptable());
		}
	}

	@Test
	void hasBetweenFifteenAndTwentyQueriesSoTheyFitInOneEmbeddingRequest() {
		assertThat(RetrievalEvalQueries.all()).hasSizeBetween(15, 20);
	}
}
