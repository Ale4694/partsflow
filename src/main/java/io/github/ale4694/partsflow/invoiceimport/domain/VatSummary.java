package io.github.ale4694.partsflow.invoiceimport.domain;

import java.math.BigDecimal;

/** One DatiRiepilogo block: the taxable amount and tax declared for a VAT rate. */
public record VatSummary(BigDecimal vatRate, BigDecimal taxableAmount, BigDecimal taxAmount) {
}
