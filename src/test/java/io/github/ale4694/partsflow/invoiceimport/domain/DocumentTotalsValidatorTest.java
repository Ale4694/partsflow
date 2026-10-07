package io.github.ale4694.partsflow.invoiceimport.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentTotalsValidatorTest {

	private DocumentLine line(String total, String rate) {
		return new DocumentLine(1, List.of(), "x", BigDecimal.ONE, "PZ", new BigDecimal(total), new BigDecimal(total),
				new BigDecimal(rate));
	}

	private VatSummary summary(String rate, String taxable) {
		return new VatSummary(new BigDecimal(rate), new BigDecimal(taxable), BigDecimal.ZERO);
	}

	private DocumentBody body(List<DocumentLine> lines, List<VatSummary> summaries) {
		return new DocumentBody("TD01", new SupplierParty("20000000001", "Ricambi Rossi Srl"), "1",
				LocalDate.of(2026, 1, 1), BigDecimal.ZERO, lines, summaries, List.of());
	}

	@Test
	void sameRateWrittenDifferentlyIsOneRate() {
		assertThatCode(() -> DocumentTotalsValidator.validate(
				body(List.of(line("10.00", "22"), line("5.00", "22.00")), List.of(summary("22.0", "15.00")))))
				.doesNotThrowAnyException();
	}

	@Test
	void oneCentOfRoundingIsTolerated() {
		assertThatCode(() -> DocumentTotalsValidator.validate(
				body(List.of(line("10.00", "22")), List.of(summary("22", "10.01")))))
				.doesNotThrowAnyException();
	}

	@Test
	void twoCentsAreNot() {
		assertThatThrownBy(() -> DocumentTotalsValidator.validate(
				body(List.of(line("10.00", "22")), List.of(summary("22", "10.02")))))
				.isInstanceOf(InvalidDocumentException.class);
	}

	@Test
	void summaryForARateWithoutLinesIsAMismatch() {
		assertThatThrownBy(() -> DocumentTotalsValidator.validate(
				body(List.of(line("10.00", "22")), List.of(summary("22", "10.00"), summary("10", "7.00")))))
				.isInstanceOf(InvalidDocumentException.class)
				.hasMessageContaining("aliquota IVA 10.00");
	}

	@Test
	void linesWithARateMissingFromTheSummaryAreAMismatch() {
		assertThatThrownBy(() -> DocumentTotalsValidator.validate(
				body(List.of(line("10.00", "22"), line("3.00", "4")), List.of(summary("22", "10.00")))))
				.isInstanceOf(InvalidDocumentException.class)
				.hasMessageContaining("aliquota IVA 4.00");
	}
}
