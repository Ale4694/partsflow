package io.github.ale4694.partsflow.invoiceimport.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Checks that the lines add up to the DatiRiepilogo blocks, VAT rate by VAT rate.
 * Shared by every import path (XML and PDF), so a draft is never created from inconsistent numbers.
 */
public final class DocumentTotalsValidator {

	/** FatturaPA allows rounding differences of one cent. */
	private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

	private DocumentTotalsValidator() {
	}

	public static void validate(DocumentBody body) {
		Map<BigDecimal, BigDecimal> fromLines = new TreeMap<>();
		for (DocumentLine line : body.lines()) {
			fromLines.merge(rateKey(line.vatRate()), line.totalPrice(), BigDecimal::add);
		}
		Map<BigDecimal, BigDecimal> fromSummaries = new TreeMap<>();
		for (VatSummary summary : body.summaries()) {
			fromSummaries.merge(rateKey(summary.vatRate()), summary.taxableAmount(), BigDecimal::add);
		}

		Set<BigDecimal> rates = new TreeSet<>(fromLines.keySet());
		rates.addAll(fromSummaries.keySet());
		for (BigDecimal rate : rates) {
			BigDecimal linesTotal = fromLines.getOrDefault(rate, BigDecimal.ZERO);
			BigDecimal summaryTotal = fromSummaries.getOrDefault(rate, BigDecimal.ZERO);
			if (linesTotal.subtract(summaryTotal).abs().compareTo(TOLERANCE) > 0) {
				throw new InvalidDocumentException("Totals do not match for VAT rate " + rate + ": lines add up to "
						+ linesTotal.setScale(2, RoundingMode.HALF_UP) + " but DatiRiepilogo declares "
						+ summaryTotal.setScale(2, RoundingMode.HALF_UP));
			}
		}
	}

	/** 22, 22.0 and 22.00 are the same rate. */
	private static BigDecimal rateKey(BigDecimal rate) {
		return rate.setScale(2, RoundingMode.HALF_UP);
	}
}
