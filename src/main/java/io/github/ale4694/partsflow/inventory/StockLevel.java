package io.github.ale4694.partsflow.inventory;

import java.math.BigDecimal;

/** One row of the stock overview: an item and how much of it is in stock now. */
public record StockLevel(Long itemId, String itemCode, String description, String unit, BigDecimal quantity,
		BigDecimal reorderThreshold) {
}
