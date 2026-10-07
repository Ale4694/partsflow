package io.github.ale4694.partsflow.invoiceimport.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

/**
 * One DettaglioLinee. {@code quantity} is null for lines that are not goods (e.g. transport costs).
 * All amounts are BigDecimal built from the text in the XML, never from double.
 */
public record DocumentLine(
		int lineNumber,
		List<ArticleCode> articleCodes,
		String description,
		BigDecimal quantity,
		String unit,
		BigDecimal unitPrice,
		BigDecimal totalPrice,
		BigDecimal vatRate) {

	/**
	 * The article codes in the order we try them against the supplier's code mappings: the supplier's own codes
	 * first, barcodes (EAN, GTIN, UPC, BARCODE) last. Inside each group the order of the XML is kept.
	 */
	public List<ArticleCode> codesByPreference() {
		return Stream.concat(
				articleCodes.stream().filter(code -> !code.isBarcode()),
				articleCodes.stream().filter(ArticleCode::isBarcode)).toList();
	}

	/**
	 * The code shown on the draft line and remembered when a person resolves it: the first code that is not a
	 * barcode, or the first code if there are only barcodes. Null when the line prints no code.
	 * FatturaPA allows several CodiceArticolo per line (EAN, supplier SKU...), see ADR 0005.
	 */
	public String supplierCode() {
		List<ArticleCode> ordered = codesByPreference();
		return ordered.isEmpty() ? null : ordered.getFirst().value();
	}
}
