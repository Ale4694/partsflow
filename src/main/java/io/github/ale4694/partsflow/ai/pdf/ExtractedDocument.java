package io.github.ale4694.partsflow.ai.pdf;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * What we ask the LLM to return for a PDF. Every value is a String on purpose: the LLM copies text, and our code
 * converts it to numbers and dates (and rejects it if it cannot). The descriptions become part of the JSON schema
 * the model is shown.
 */
public record ExtractedDocument(
		@JsonPropertyDescription("One of INVOICE, CREDIT_NOTE, DELIVERY_NOTE (Fattura, Nota di credito, DDT / Documento di trasporto)")
		String documentType,
		@JsonPropertyDescription("Name of the company that ISSUED the document (the supplier), not the recipient")
		String supplierName,
		@JsonPropertyDescription("Partita IVA / VAT number of the issuing company, digits and letters only, as printed")
		String supplierVatNumber,
		@JsonPropertyDescription("Document number exactly as printed")
		String number,
		@JsonPropertyDescription("Document date as yyyy-MM-dd")
		String date,
		@JsonPropertyDescription("ISO currency code, normally EUR")
		String currency,
		@JsonPropertyDescription("Grand total of the document, VAT included, with a dot as decimal separator. null for delivery notes without prices")
		String totalAmount,
		@JsonPropertyDescription("Every line of goods or services, in document order")
		List<Line> lines) {

	public record Line(
			@JsonPropertyDescription("The supplier's article code printed on the line, null if there is none")
			String supplierCode,
			@JsonPropertyDescription("Line description exactly as printed")
			String description,
			@JsonPropertyDescription("Quantity as a plain decimal number, null for lines that are not goods (e.g. transport)")
			String quantity,
			@JsonPropertyDescription("Unit of measure as printed (PZ, KG, LT...), null if absent")
			String unit,
			@JsonPropertyDescription("Price of one unit before VAT, plain decimal number, null if not printed")
			String unitPrice,
			@JsonPropertyDescription("Total of the line before VAT, plain decimal number, null if not printed")
			String totalPrice,
			@JsonPropertyDescription("VAT rate in percent, e.g. 22, null if not printed")
			String vatRate) {
	}
}
