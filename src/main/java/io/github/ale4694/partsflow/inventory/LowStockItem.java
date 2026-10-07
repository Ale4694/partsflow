package io.github.ale4694.partsflow.inventory;

import java.math.BigDecimal;

public record LowStockItem(Long itemId, String itemCode, String description, String unit, BigDecimal quantity,
		BigDecimal reorderThreshold) {
}
