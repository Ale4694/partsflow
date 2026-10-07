package io.github.ale4694.partsflow.invoiceimport.domain;

import java.math.BigDecimal;

public final class Quantities {

	/** Quantities are stored with 3 decimals. */
	public static final int MAX_DECIMALS = 3;

	private Quantities() {
	}

	/** "2.00000000" becomes 2; "2.0005" is rejected rather than silently rounded. */
	public static BigDecimal normalize(BigDecimal value) {
		BigDecimal stripped = value.stripTrailingZeros();
		if (stripped.scale() > MAX_DECIMALS) {
			throw new InvalidDocumentException(
					"Quantity " + value.toPlainString() + " has more than " + MAX_DECIMALS + " decimals");
		}
		return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
	}
}
