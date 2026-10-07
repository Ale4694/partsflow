package io.github.ale4694.partsflow.invoiceimport.domain;

import java.math.BigDecimal;

public final class StockEffects {

	private StockEffects() {
	}

	/**
	 * How much a line changes the stock: positive adds, negative removes.
	 * Invoices and delivery notes add what they list; credit notes reverse it.
	 */
	public static BigDecimal stockDelta(Document document, DocumentLine line) {
		return switch (document) {
			case Invoice invoice -> line.quantity();
			case DeliveryNote deliveryNote -> line.quantity();
			case CreditNote creditNote -> line.quantity().negate();
		};
	}
}
