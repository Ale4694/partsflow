package io.github.ale4694.partsflow.ai.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ale4694.partsflow.ai.AiIntegrationTestBase;
import io.github.ale4694.partsflow.ai.search.ItemEmbeddingIndexer;
import io.github.ale4694.partsflow.ai.search.ItemSearchService;
import io.github.ale4694.partsflow.catalog.Item;
import io.github.ale4694.partsflow.inventory.InventoryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/** The assistant's searchItems tool uses the hybrid search: it finds an item described in other words. */
@TestPropertySource(properties = "partsflow.embeddings.enabled=true")
class AssistantSearchRagTest extends AiIntegrationTestBase {

	@Autowired
	InventoryService inventory;
	@Autowired
	ItemSearchService search;
	@Autowired
	ItemEmbeddingIndexer indexer;

	@Test
	void searchItemsFindsByMeaning() {
		int n = next();
		Item cartridge = newItem("AST-RAG-" + n, "Cartuccia lubrificante motore 1.2 FIRE");
		newItem("AST-RAG-BRK-" + n, "Pastiglie freni posteriori");
		indexer.indexStale();
		indexer.resume();

		Object result = new InventoryAssistantTools(inventory, items, search, 5).searchItems("filtro olio Fiat Panda");

		assertThat(result).isInstanceOf(List.class);
		assertThat(((List<?>) result)).extracting(found -> ((InventoryAssistantTools.ItemFound) found).itemCode())
				.contains(cartridge.getCode());
	}
}
