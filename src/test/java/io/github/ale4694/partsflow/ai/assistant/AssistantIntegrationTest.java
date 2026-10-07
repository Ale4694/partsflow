package io.github.ale4694.partsflow.ai.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.ale4694.partsflow.ai.AiIntegrationTestBase;
import io.github.ale4694.partsflow.ai.search.ItemSearchService;
import io.github.ale4694.partsflow.catalog.Item;
import io.github.ale4694.partsflow.inventory.InventoryService;
import io.github.ale4694.partsflow.inventory.MovementType;
import io.github.ale4694.partsflow.inventory.StockMovementRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class AssistantIntegrationTest extends AiIntegrationTestBase {

	@Autowired
	InventoryService inventory;
	@Autowired
	ItemSearchService search;

	private InventoryAssistantTools tools(int maxCalls) {
		return new InventoryAssistantTools(inventory, items, search, maxCalls);
	}

	private Item itemWithStock(String quantity, String threshold) {
		int n = next();
		Item item = items.save(new Item("AST-" + n, "Assistant test brake disc " + n, "PZ", new BigDecimal(threshold)));
		inventory.record(new StockMovementRequest(item.getId(), MovementType.IN, new BigDecimal(quantity), "setup", "doc-1"));
		return item;
	}

	@Test
	void getStockReportsQuantityAndThresholdStatus() {
		Item item = itemWithStock("3", "5");

		Object result = tools(5).getStock(item.getCode());

		assertThat(result).isInstanceOfSatisfying(InventoryAssistantTools.StockInfo.class, info -> {
			assertThat(info.itemCode()).isEqualTo(item.getCode());
			assertThat(info.quantity()).isEqualByComparingTo("3");
			assertThat(info.belowReorderThreshold()).isTrue();
		});
	}

	@Test
	void unknownItemCodeGetsAHelpfulMessageInsteadOfAnError() {
		assertThat(tools(5).getStock("DOES-NOT-EXIST")).asString().contains("No item with code").contains("searchItems");
		assertThat(tools(5).getRecentMovements("DOES-NOT-EXIST", 5)).asString().contains("No item with code");
	}

	@Test
	void searchFindsItemsByDescription() {
		Item item = itemWithStock("1", "0");

		Object result = tools(5).searchItems("Assistant test brake disc " + item.getCode().substring(4));

		assertThat(result).isInstanceOf(List.class);
		assertThat((List<?>) result).anySatisfy(found -> assertThat(found.toString()).contains(item.getCode()));
	}

	@Test
	void recentMovementsAreNewestFirstAndTheLimitIsClamped() {
		Item item = itemWithStock("10", "0");
		inventory.record(new StockMovementRequest(item.getId(), MovementType.OUT, BigDecimal.ONE, "sale", null));

		Object result = tools(5).getRecentMovements(item.getCode(), 1000);

		assertThat(result).isInstanceOf(List.class);
		List<?> movements = (List<?>) result;
		assertThat(movements).hasSize(2);
		assertThat(((InventoryAssistantTools.MovementInfo) movements.getFirst()).type()).isEqualTo("OUT");
	}

	@Test
	void lowStockListIncludesItemsBelowTheirThreshold() {
		Item item = itemWithStock("1", "5");

		Object result = tools(5).listItemsBelowReorderThreshold();

		assertThat(result.toString()).contains(item.getCode());
	}

	@Test
	void springAiDiscoversTheToolsAndCallsThemWithJsonArguments() {
		Item item = itemWithStock("7", "0");
		ToolCallback[] callbacks = ToolCallbacks.from(tools(5));

		assertThat(callbacks).extracting(c -> c.getToolDefinition().name())
				.containsExactlyInAnyOrder("getStock", "searchItems", "listItemsBelowReorderThreshold",
						"getRecentMovements");
		ToolCallback getStock = java.util.Arrays.stream(callbacks)
				.filter(c -> c.getToolDefinition().name().equals("getStock")).findFirst().orElseThrow();
		assertThat(getStock.getToolDefinition().description()).contains("Current stock");

		// this is exactly what the model sends, and what it gets back
		String json = getStock.call("{\"itemCode\": \"" + item.getCode() + "\"}");

		assertThat(json).contains("\"itemCode\"").contains(item.getCode()).contains("\"quantity\":7");
	}

	@Test
	void theToolCallBudgetStopsFurtherCalls() {
		Item item = itemWithStock("3", "0");
		InventoryAssistantTools tools = tools(2);

		assertThat(tools.getStock(item.getCode())).isInstanceOf(InventoryAssistantTools.StockInfo.class);
		assertThat(tools.getStock(item.getCode())).isInstanceOf(InventoryAssistantTools.StockInfo.class);
		assertThat(tools.getStock(item.getCode())).isEqualTo(InventoryAssistantTools.LIMIT_REACHED);
		assertThat(tools.listItemsBelowReorderThreshold()).isEqualTo(InventoryAssistantTools.LIMIT_REACHED);
	}

	@Test
	void assistantEndpointPassesTheQuestionAndAGroundedSystemPromptToTheModel() throws Exception {
		when(gateway.converse(eq("inventory-assistant"), any(), any(), any(InventoryAssistantTools.class)))
				.thenReturn("BRK-001 has 3 pieces in stock.");

		mvc.perform(post("/api/ai/assistant").contentType(MediaType.APPLICATION_JSON)
				.content("{\"question\": \"How many BRK-001 do we have?\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.answer").value("BRK-001 has 3 pieces in stock."))
				.andExpect(jsonPath("$.toolCalls").value(0));

		ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> question = ArgumentCaptor.forClass(String.class);
		verify(gateway).converse(eq("inventory-assistant"), system.capture(), question.capture(), any());
		assertThat(question.getValue()).isEqualTo("How many BRK-001 do we have?");
		assertThat(system.getValue()).contains("ONLY on data returned by your tools").contains("You can only read data");
	}

	@Test
	void blankOrOversizedQuestionsAreRejected() throws Exception {
		mvc.perform(post("/api/ai/assistant").contentType(MediaType.APPLICATION_JSON).content("{\"question\": \" \"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/ai/assistant").contentType(MediaType.APPLICATION_JSON)
				.content("{\"question\": \"" + "x".repeat(501) + "\"}"))
				.andExpect(status().isBadRequest());
	}
}
