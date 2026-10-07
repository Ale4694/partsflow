package io.github.ale4694.partsflow.invoiceimport.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentLineTest {

	private static DocumentLine lineWith(ArticleCode... codes) {
		return new DocumentLine(1, List.of(codes), "Some part", new BigDecimal("1"), "PZ", new BigDecimal("10"),
				new BigDecimal("10"), new BigDecimal("22"));
	}

	@Test
	void supplierCodeSkipsABarcodePrintedFirst() {
		DocumentLine line = lineWith(new ArticleCode("EAN", "8000000000011"), new ArticleCode("FORNITORE", "RR-BRK-001"));

		assertThat(line.supplierCode()).isEqualTo("RR-BRK-001");
		assertThat(line.codesByPreference()).extracting(ArticleCode::value)
				.containsExactly("RR-BRK-001", "8000000000011");
	}

	@Test
	void supplierCodePrintedFirstStaysFirst() {
		DocumentLine line = lineWith(new ArticleCode("FORNITORE", "RR-BRK-001"), new ArticleCode("EAN", "8000000000011"));

		assertThat(line.supplierCode()).isEqualTo("RR-BRK-001");
	}

	@Test
	void withSeveralNonBarcodeCodesTheFirstOneInTheXmlWins() {
		DocumentLine line = lineWith(new ArticleCode("GTIN", "8000000000028"), new ArticleCode("INTERNO", "A-1"),
				new ArticleCode("FORNITORE", "B-2"));

		assertThat(line.supplierCode()).isEqualTo("A-1");
		assertThat(line.codesByPreference()).extracting(ArticleCode::value)
				.containsExactly("A-1", "B-2", "8000000000028");
	}

	@Test
	void barcodeTypesAreRecognisedIgnoringCaseAndSpaces() {
		assertThat(new ArticleCode(" ean ", "1").isBarcode()).isTrue();
		assertThat(new ArticleCode("Gtin", "1").isBarcode()).isTrue();
		assertThat(new ArticleCode("UPC", "1").isBarcode()).isTrue();
		assertThat(new ArticleCode("barcode", "1").isBarcode()).isTrue();
		assertThat(new ArticleCode("FORNITORE", "1").isBarcode()).isFalse();
		assertThat(new ArticleCode("", "1").isBarcode()).isFalse();
	}

	@Test
	void whenOnlyBarcodesArePrintedTheFirstOneIsUsed() {
		DocumentLine line = lineWith(new ArticleCode("EAN", "8000000000011"), new ArticleCode("UPC", "012345678905"));

		assertThat(line.supplierCode()).isEqualTo("8000000000011");
	}

	@Test
	void aLineWithoutCodesHasNoSupplierCode() {
		assertThat(lineWith().supplierCode()).isNull();
		assertThat(lineWith().codesByPreference()).isEmpty();
	}
}
