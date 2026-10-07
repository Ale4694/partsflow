package io.github.ale4694.partsflow.ai.search;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ale4694.partsflow.TestcontainersConfiguration;
import io.github.ale4694.partsflow.ai.search.ItemSearchService.FallbackReason;
import io.github.ale4694.partsflow.ai.search.ItemSearchService.Mode;
import io.github.ale4694.partsflow.ai.search.ItemSearchService.SearchResult;
import io.github.ale4694.partsflow.catalog.Item;
import io.github.ale4694.partsflow.catalog.ItemRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/** Embeddings configured but nothing indexed yet: text search, and the response says so. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = { "partsflow.ai.api-key=fake-key", "partsflow.embeddings.model=never-indexed-model" })
class ItemSearchFallbackTest {

	@Autowired
	ItemSearchService service;
	@Autowired
	ItemRepository items;
	@Autowired
	FakeEmbeddingModel model;

	@Test
	void withoutAnyEmbeddingTheSearchIsTextOnlyAndSendsNothing() {
		items.save(new Item("FB-1", "Filtro aria abitacolo", "PZ", BigDecimal.ZERO)); // saved by repository: no event
		model.reset();

		SearchResult result = service.search("filtro aria", 5, true);

		assertThat(result.mode()).isEqualTo(Mode.TEXT);
		assertThat(result.fallbackReason()).isEqualTo(FallbackReason.NOT_INDEXED);
		assertThat(result.results()).extracting(ItemSearchService.Hit::code).contains("FB-1");
		assertThat(model.batchSizes()).isEmpty();
	}
}
