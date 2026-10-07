package io.github.ale4694.partsflow.inventory;

import java.math.BigDecimal;
import java.time.Instant;

public record StockMovementResponse(Long id, Long itemId, MovementType type, BigDecimal quantity, String reason,
		String sourceDocument, Instant createdAt) {

	static StockMovementResponse from(StockMovement movement) {
		return new StockMovementResponse(movement.getId(), movement.getItemId(), movement.getType(),
				movement.getQuantity(), movement.getReason(), movement.getSourceDocument(), movement.getCreatedAt());
	}
}
