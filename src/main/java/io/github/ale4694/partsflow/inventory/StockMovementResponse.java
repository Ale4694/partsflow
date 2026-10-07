package io.github.ale4694.partsflow.inventory;

import java.math.BigDecimal;
import java.time.Instant;

public record StockMovementResponse(Long id, Long itemId, String itemCode, MovementType type, BigDecimal quantity,
		String reason, String sourceDocument, Instant createdAt) {

	static StockMovementResponse from(StockMovement movement, String itemCode) {
		return new StockMovementResponse(movement.getId(), movement.getItemId(), itemCode, movement.getType(),
				movement.getQuantity(), movement.getReason(), movement.getSourceDocument(), movement.getCreatedAt());
	}
}
