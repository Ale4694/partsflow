package io.github.ale4694.partsflow.ai.assistant;

import io.github.ale4694.partsflow.ai.search.ItemSearchService;
import io.github.ale4694.partsflow.catalog.Item;
import io.github.ale4694.partsflow.catalog.ItemRepository;
import io.github.ale4694.partsflow.inventory.InventoryService;
import io.github.ale4694.partsflow.inventory.StockMovementResponse;
import io.github.ale4694.partsflow.inventory.StockResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * What the inventory assistant is allowed to do: READ stock data. There is deliberately no tool that changes
 * anything, so whatever the model is asked (or tricked into) it cannot alter inventory.
 * <p>
 * One instance is created per question. It counts the tool calls and refuses to run more than
 * {@code maxCalls}, which bounds how many steps the model can take before it has to answer.
 */
public class InventoryAssistantTools {

	static final String LIMIT_REACHED = "Tool call limit reached. Do not call more tools; answer with what you already know, "
			+ "or say that you could not find out.";

	private final InventoryService inventory;
	private final ItemRepository items;
	private final ItemSearchService search;
	private final int maxCalls;
	private int calls;

	public InventoryAssistantTools(InventoryService inventory, ItemRepository items,
			ItemSearchService search, int maxCalls) {
		this.inventory = inventory;
		this.items = items;
		this.search = search;
		this.maxCalls = maxCalls;
	}

	public int calls() {
		return calls;
	}

	public record ItemFound(String itemCode, String description, String unit) {
	}

	public record MovementInfo(String type, BigDecimal quantity, String reason, String sourceDocument, Instant at) {
	}

	@Tool(description = "Current stock of one item, with its unit and reorder threshold. Takes the internal item code, e.g. BRK-001.")
	public Object getStock(@ToolParam(description = "Internal item code") String itemCode) {
		if (!allowed()) {
			return LIMIT_REACHED;
		}
		return items.findByCode(itemCode.trim())
				.<Object>map(item -> {
					StockResponse stock = inventory.getStock(item.getId());
					return new StockInfo(stock.itemCode(), item.getDescription(), stock.quantity(), stock.unit(),
							stock.reorderThreshold(), stock.quantity().compareTo(stock.reorderThreshold()) < 0);
				})
				.orElse("No item with code " + itemCode + " exists. Use searchItems to look it up by description.");
	}

	public record StockInfo(String itemCode, String description, BigDecimal quantity, String unit,
			BigDecimal reorderThreshold, boolean belowReorderThreshold) {
	}

	@Tool(description = "Find catalog items by (part of) a description, e.g. 'brake pads' or 'filtro olio Fiat Panda'. Searches by meaning as well as by words. Returns up to 5 items with their codes.")
	public Object searchItems(@ToolParam(description = "Words from the item description") String text) {
		if (!allowed()) {
			return LIMIT_REACHED;
		}
		// hybrid search: finds items by meaning ("filtro olio" finds a "cartuccia lubrificante") and by spelling
		List<ItemFound> found = search.search(text, 5, true).results().stream()
				.map(hit -> new ItemFound(hit.code(), hit.description(), hit.unit()))
				.toList();
		return found.isEmpty() ? "No similar item found." : found;
	}

	@Tool(description = "List the items whose stock is below their reorder threshold (up to 20), with the current quantity and the threshold.")
	public Object listItemsBelowReorderThreshold() {
		if (!allowed()) {
			return LIMIT_REACHED;
		}
		var page = inventory.listLowStock(PageRequest.of(0, 20));
		return page.content().isEmpty() ? "No item is below its reorder threshold." : page.content();
	}

	@Tool(description = "The most recent stock movements (IN or OUT) of one item, newest first. Takes the internal item code.")
	public Object getRecentMovements(@ToolParam(description = "Internal item code") String itemCode,
			@ToolParam(description = "How many movements, 1 to 20") int limit) {
		if (!allowed()) {
			return LIMIT_REACHED;
		}
		Item item = items.findByCode(itemCode.trim()).orElse(null);
		if (item == null) {
			return "No item with code " + itemCode + " exists. Use searchItems to look it up by description.";
		}
		int size = Math.max(1, Math.min(limit, 20));
		List<StockMovementResponse> movements = inventory
				.listMovements(item.getId(), PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "createdAt")))
				.content();
		return movements.isEmpty() ? "This item has no movements yet."
				: movements.stream()
						.map(m -> new MovementInfo(m.type().name(), m.quantity(), m.reason(), m.sourceDocument(),
								m.createdAt()))
						.toList();
	}

	private boolean allowed() {
		calls++;
		return calls <= maxCalls;
	}
}
