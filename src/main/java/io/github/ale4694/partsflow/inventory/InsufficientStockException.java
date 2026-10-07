package io.github.ale4694.partsflow.inventory;

import io.github.ale4694.partsflow.common.ConflictException;
import java.math.BigDecimal;

/** An OUT movement would make the stock negative. Mapped to HTTP 409 like any other conflict. */
public class InsufficientStockException extends ConflictException {

	public InsufficientStockException(Long itemId, BigDecimal available, BigDecimal requested) {
		super("Giacenza insufficiente per l'articolo " + itemId + ": disponibili "
				+ available.stripTrailingZeros().toPlainString() + ", richiesti "
				+ requested.stripTrailingZeros().toPlainString());
	}
}
