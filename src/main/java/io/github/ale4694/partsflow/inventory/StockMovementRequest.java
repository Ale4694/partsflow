package io.github.ale4694.partsflow.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record StockMovementRequest(
		@NotNull Long itemId,
		@NotNull MovementType type,
		@NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 3) BigDecimal quantity,
		@NotBlank @Size(max = 200) String reason,
		@Size(max = 200) String sourceDocument) {
}
