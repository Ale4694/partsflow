package io.github.ale4694.partsflow.invoiceimport.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class StockEffectsTest {

	private final DocumentLine line = new DocumentLine(1, List.of(), "Brake pads", new BigDecimal("4"), "PZ",
			new BigDecimal("10"), new BigDecimal("40"), new BigDecimal("22"));
	private final DocumentBody body = new DocumentBody("TD01", new SupplierParty("20000000001", "Ricambi Rossi Srl"),
			"1", LocalDate.of(2026, 1, 1), new BigDecimal("48.80"), List.of(line), List.of(), List.of());

	@Test
	void invoiceAddsTheQuantity() {
		assertThat(StockEffects.stockDelta(new Invoice(body), line)).isEqualByComparingTo("4");
	}

	@Test
	void creditNoteReversesTheQuantity() {
		assertThat(StockEffects.stockDelta(new CreditNote(body), line)).isEqualByComparingTo("-4");
	}
}
