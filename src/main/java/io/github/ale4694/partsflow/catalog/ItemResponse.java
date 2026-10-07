package io.github.ale4694.partsflow.catalog;

import java.math.BigDecimal;

public record ItemResponse(Long id, String code, String description, String unit, BigDecimal reorderThreshold) {

	static ItemResponse from(Item item) {
		return new ItemResponse(item.getId(), item.getCode(), item.getDescription(), item.getUnit(),
				item.getReorderThreshold());
	}
}
