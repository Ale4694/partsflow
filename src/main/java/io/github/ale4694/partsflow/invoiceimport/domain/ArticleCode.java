package io.github.ale4694.partsflow.invoiceimport.domain;

import java.util.Locale;
import java.util.Set;

/** FatturaPA CodiceArticolo: a typed code (EAN, supplier SKU...) printed on an invoice line. */
public record ArticleCode(String type, String value) {

	/** CodiceTipo values that name a product barcode, not the supplier's own article code. Compared ignoring case. */
	private static final Set<String> BARCODE_TYPES = Set.of("EAN", "GTIN", "UPC", "BARCODE");

	/** A barcode identifies the product worldwide; it is not the code the supplier uses in its price list. */
	public boolean isBarcode() {
		return BARCODE_TYPES.contains(type.trim().toUpperCase(Locale.ROOT));
	}
}
