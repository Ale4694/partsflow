package io.github.ale4694.partsflow.invoiceimport.domain;

import java.math.BigDecimal;
import java.util.List;

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
}
