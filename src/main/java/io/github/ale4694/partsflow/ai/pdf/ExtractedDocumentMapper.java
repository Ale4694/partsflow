package io.github.ale4694.partsflow.ai.pdf;

import io.github.ale4694.partsflow.invoiceimport.domain.ArticleCode;
import io.github.ale4694.partsflow.invoiceimport.domain.CreditNote;
import io.github.ale4694.partsflow.invoiceimport.domain.DeliveryNote;
import io.github.ale4694.partsflow.invoiceimport.domain.Document;
import io.github.ale4694.partsflow.invoiceimport.domain.DocumentBody;
import io.github.ale4694.partsflow.invoiceimport.domain.DocumentLine;
import io.github.ale4694.partsflow.invoiceimport.domain.InvalidDocumentException;
import io.github.ale4694.partsflow.invoiceimport.domain.Invoice;
import io.github.ale4694.partsflow.invoiceimport.domain.Quantities;
import io.github.ale4694.partsflow.invoiceimport.domain.SupplierParty;
import io.github.ale4694.partsflow.invoiceimport.domain.VatSummary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * Validates what the LLM extracted and turns it into the same domain model the XML import uses.
 * The LLM is not trusted: anything that cannot be parsed, or whose numbers do not add up, is rejected
 * with a 422 instead of becoming a draft.
 */
@Component
public class ExtractedDocumentMapper {

	/** The document total must match the lines within a few cents (VAT is rounded per rate). */
	private static final BigDecimal TOTAL_TOLERANCE = new BigDecimal("0.05");

	public Document toDocument(ExtractedDocument extracted) {
		if (extracted == null) {
			throw new InvalidDocumentException("L'AI non ha restituito alcun documento");
		}
		String type = text(extracted.documentType()).toUpperCase(Locale.ROOT);
		if (!List.of("INVOICE", "CREDIT_NOTE", "DELIVERY_NOTE").contains(type)) {
			throw new InvalidDocumentException("Tipo di documento non riconosciuto (ricevuto '" + type + "')");
		}
		boolean priced = !type.equals("DELIVERY_NOTE");

		String currency = text(extracted.currency()).toUpperCase(Locale.ROOT);
		if (!currency.isEmpty() && !currency.equals("EUR")) {
			throw new InvalidDocumentException("Sono supportati solo documenti in EUR, trovato " + currency);
		}

		if (extracted.lines() == null || extracted.lines().isEmpty()) {
			throw new InvalidDocumentException("Nel documento non è stata trovata nessuna riga");
		}
		List<DocumentLine> lines = new ArrayList<>();
		for (int i = 0; i < extracted.lines().size(); i++) {
			lines.add(toLine(i + 1, extracted.lines().get(i), priced));
		}

		List<VatSummary> summaries = summaries(lines);
		BigDecimal linesTotal = summaries.stream().map(s -> s.taxableAmount().add(s.taxAmount())).reduce(
				BigDecimal.ZERO, BigDecimal::add);
		BigDecimal total;
		if (priced) {
			total = decimal(required(extracted.totalAmount(), "importo totale"), "importo totale");
			if (linesTotal.subtract(total).abs().compareTo(TOTAL_TOLERANCE) > 0) {
				throw new InvalidDocumentException("Le righe estratte sommano " + linesTotal
						+ " (IVA inclusa) ma il totale del documento è " + total
						+ ": l'estrazione è probabilmente sbagliata o incompleta");
			}
		}
		else {
			total = linesTotal;
		}

		DocumentBody body = new DocumentBody(
				switch (type) {
					case "INVOICE" -> "TD01";
					case "CREDIT_NOTE" -> "TD04";
					default -> "DDT";
				},
				new SupplierParty(vat(extracted.supplierVatNumber()), required(extracted.supplierName(), "nome del fornitore")),
				required(extracted.number(), "numero del documento"),
				date(extracted.date()),
				total, lines, summaries, List.of());

		return switch (type) {
			case "INVOICE" -> new Invoice(body);
			case "CREDIT_NOTE" -> new CreditNote(body);
			default -> new DeliveryNote(body);
		};
	}

	private DocumentLine toLine(int number, ExtractedDocument.Line line, boolean priced) {
		BigDecimal quantity = blank(line.quantity()) ? null : Quantities.normalize(decimal(line.quantity(), "quantità"));
		BigDecimal unitPrice = blank(line.unitPrice()) ? null : decimal(line.unitPrice(), "prezzo unitario");
		BigDecimal totalPrice = blank(line.totalPrice()) ? null : decimal(line.totalPrice(), "totale riga");
		BigDecimal vatRate = blank(line.vatRate()) ? null : decimal(line.vatRate(), "aliquota IVA");
		if (priced && (totalPrice == null || vatRate == null)) {
			throw new InvalidDocumentException("La riga " + number + " non ha il totale o l'aliquota IVA");
		}
		List<ArticleCode> codes = blank(line.supplierCode()) ? List.of()
				: List.of(new ArticleCode("SUPPLIER", line.supplierCode().trim()));
		return new DocumentLine(number, codes, required(line.description(), "descrizione della riga " + number), quantity,
				blank(line.unit()) ? null : line.unit().trim(), unitPrice,
				totalPrice == null ? BigDecimal.ZERO : totalPrice, vatRate == null ? BigDecimal.ZERO : vatRate);
	}

	/** Taxable amount and VAT per rate, calculated by us from the lines. */
	private List<VatSummary> summaries(List<DocumentLine> lines) {
		Map<BigDecimal, BigDecimal> taxableByRate = new TreeMap<>();
		for (DocumentLine line : lines) {
			taxableByRate.merge(line.vatRate().setScale(2, RoundingMode.HALF_UP), line.totalPrice(), BigDecimal::add);
		}
		List<VatSummary> summaries = new ArrayList<>();
		taxableByRate.forEach((rate, taxable) -> summaries.add(new VatSummary(rate, taxable,
				taxable.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))));
		return summaries;
	}

	/** "IT 01234567890" and "it01234567890" both become 01234567890. */
	private String vat(String raw) {
		String vat = required(raw, "partita IVA del fornitore").replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
		if (vat.length() == 13 && vat.startsWith("IT")) {
			vat = vat.substring(2);
		}
		if (!vat.matches("[A-Z0-9]{8,28}")) {
			throw new InvalidDocumentException("La partita IVA del fornitore '" + raw + "' non sembra valida");
		}
		return vat;
	}

	private LocalDate date(String raw) {
		try {
			return LocalDate.parse(required(raw, "data"));
		}
		catch (DateTimeParseException ex) {
			throw new InvalidDocumentException("La data del documento '" + raw + "' non è una data valida (aaaa-mm-gg)");
		}
	}

	private BigDecimal decimal(String raw, String what) {
		try {
			return new BigDecimal(raw.trim());
		}
		catch (NumberFormatException ex) {
			throw new InvalidDocumentException("Il valore '" + raw + "' (" + what + ") non è un numero decimale semplice");
		}
	}

	private String required(String value, String what) {
		if (blank(value)) {
			throw new InvalidDocumentException("Nel documento non è stato trovato: " + what);
		}
		return value.trim();
	}

	private boolean blank(String value) {
		return value == null || value.isBlank() || value.trim().equalsIgnoreCase("null");
	}

	private String text(String value) {
		return value == null ? "" : value.trim();
	}
}
