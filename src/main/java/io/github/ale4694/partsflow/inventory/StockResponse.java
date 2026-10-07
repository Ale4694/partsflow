package io.github.ale4694.partsflow.inventory;

import java.math.BigDecimal;

public record StockResponse(Long itemId, String itemCode, BigDecimal quantity, String unit,
		BigDecimal reorderThreshold) {
}
