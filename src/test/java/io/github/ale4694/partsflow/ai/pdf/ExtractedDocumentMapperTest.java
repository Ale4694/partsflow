package io.github.ale4694.partsflow.ai.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.ale4694.partsflow.invoiceimport.domain.CreditNote;
import io.github.ale4694.partsflow.invoiceimport.domain.DeliveryNote;
import io.github.ale4694.partsflow.invoiceimport.domain.Document;
import io.github.ale4694.partsflow.invoiceimport.domain.InvalidDocumentException;
import io.github.ale4694.partsflow.invoiceimport.domain.Invoice;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExtractedDocumentMapperTest {

	private final ExtractedDocumentMapper mapper = new ExtractedDocumentMapper();

	private ExtractedDocument.Line line(String code, String description, String quantity, String unitPrice,
			String total, String vat) {
		return new ExtractedDocument.Line(code, description, quantity, "PZ", unitPrice, total, vat);
	}

	/** 10 x 25.00 + 6 x 40.00 = 490.00 + 22% VAT (107.80) = 597.80 */
	private ExtractedDocument invoice(String type, String total) {
		return new ExtractedDocument(type, "Ricambi Rossi Srl", "IT 20000000001", "FT-1/2026", "2026-03-10", "EUR",
				total, List.of(line("RR-BRK-001", "Front brake pad set", "10", "25.00", "250.00", "22"),
						line("RR-OIL-530", "Engine oil 5W-30", "6.0", "40.00", "240.00", "22")));
	}

	@Test
	void validInvoiceBecomesADomainInvoice() {
		Document document = mapper.toDocument(invoice("INVOICE", "597.80"));

		assertThat(document).isInstanceOf(Invoice.class);
		assertThat(document.body().tipoDocumento()).isEqualTo("TD01");
		assertThat(document.body().supplier().vatNumber()).isEqualTo("20000000001"); // "IT " prefix removed
		assertThat(document.body().date()).isEqualTo(LocalDate.of(2026, 3, 10));
		assertThat(document.body().total()).isEqualByComparingTo("597.80");
		assertThat(document.body().lines()).hasSize(2);
		assertThat(document.body().lines().get(1).quantity()).isEqualByComparingTo("6");
		assertThat(document.body().lines().getFirst().articleCodes()).singleElement()
				.satisfies(code -> assertThat(code.value()).isEqualTo("RR-BRK-001"));
		assertThat(document.body().summaries()).singleElement().satisfies(summary -> {
			assertThat(summary.taxableAmount()).isEqualByComparingTo("490.00");
			assertThat(summary.taxAmount()).isEqualByComparingTo("107.80");
		});
	}

	@Test
	void creditNoteBecomesACreditNote() {
		assertThat(mapper.toDocument(invoice("CREDIT_NOTE", "597.80"))).isInstanceOf(CreditNote.class);
	}

	@Test
	void anExtractionWhoseTotalDoesNotMatchTheLinesIsRejected() {
		assertThatThrownBy(() -> mapper.toDocument(invoice("INVOICE", "1597.80")))
				.isInstanceOf(InvalidDocumentException.class)
				.hasMessageContaining("597.80")
				.hasMessageContaining("1597.80");
	}

	@Test
	void deliveryNoteWithoutPricesIsAccepted() {
		ExtractedDocument ddt = new ExtractedDocument("DELIVERY_NOTE", "Ricambi Rossi Srl", "20000000001", "DDT-7",
				"2026-03-09", null, null,
				List.of(line("RR-BRK-001", "Front brake pad set", "10", null, null, null)));

		Document document = mapper.toDocument(ddt);

		assertThat(document).isInstanceOf(DeliveryNote.class);
		assertThat(document.body().tipoDocumento()).isEqualTo("DDT");
		assertThat(document.body().total()).isEqualByComparingTo("0");
	}

	@Test
	void unreadableValuesAreRejected() {
		ExtractedDocument base = invoice("INVOICE", "597.80");
		assertThatThrownBy(() -> mapper.toDocument(withDate(base, "10/03/2026")))
				.isInstanceOf(InvalidDocumentException.class).hasMessageContaining("aaaa-mm-gg");
		assertThatThrownBy(() -> mapper.toDocument(withTotal(base, "5.978,00")))
				.isInstanceOf(InvalidDocumentException.class).hasMessageContaining("numero decimale semplice");
		assertThatThrownBy(() -> mapper.toDocument(invoice("PURCHASE_ORDER", "597.80")))
				.isInstanceOf(InvalidDocumentException.class).hasMessageContaining("Tipo di documento");
	}

	@Test
	void missingEssentialsAreRejected() {
		ExtractedDocument noVat = new ExtractedDocument("INVOICE", "Ricambi Rossi Srl", "null", "FT-1/2026",
				"2026-03-10", "EUR", "597.80", invoice("INVOICE", "597.80").lines());
		assertThatThrownBy(() -> mapper.toDocument(noVat)).isInstanceOf(InvalidDocumentException.class)
				.hasMessageContaining("partita IVA");

		ExtractedDocument noLines = new ExtractedDocument("INVOICE", "Ricambi Rossi Srl", "20000000001", "FT-1/2026",
				"2026-03-10", "EUR", "0", List.of());
		assertThatThrownBy(() -> mapper.toDocument(noLines)).isInstanceOf(InvalidDocumentException.class);

		assertThatThrownBy(() -> mapper.toDocument(null)).isInstanceOf(InvalidDocumentException.class);
	}

	@Test
	void foreignCurrencyAndFractionalQuantitiesAreRejected() {
		ExtractedDocument base = invoice("INVOICE", "597.80");
		ExtractedDocument usd = new ExtractedDocument(base.documentType(), base.supplierName(),
				base.supplierVatNumber(), base.number(), base.date(), "USD", base.totalAmount(), base.lines());
		assertThatThrownBy(() -> mapper.toDocument(usd)).isInstanceOf(InvalidDocumentException.class)
				.hasMessageContaining("EUR");

		ExtractedDocument precise = new ExtractedDocument(base.documentType(), base.supplierName(),
				base.supplierVatNumber(), base.number(), base.date(), "EUR", base.totalAmount(),
				List.of(line("X", "Thing", "1.0005", "1", "1", "22")));
		assertThatThrownBy(() -> mapper.toDocument(precise)).isInstanceOf(InvalidDocumentException.class)
				.hasMessageContaining("decimali");
	}

	private ExtractedDocument withDate(ExtractedDocument d, String date) {
		return new ExtractedDocument(d.documentType(), d.supplierName(), d.supplierVatNumber(), d.number(), date,
				d.currency(), d.totalAmount(), d.lines());
	}

	private ExtractedDocument withTotal(ExtractedDocument d, String total) {
		return new ExtractedDocument(d.documentType(), d.supplierName(), d.supplierVatNumber(), d.number(), d.date(),
				d.currency(), total, d.lines());
	}
}
