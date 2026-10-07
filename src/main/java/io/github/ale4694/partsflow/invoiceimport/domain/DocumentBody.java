package io.github.ale4694.partsflow.invoiceimport.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Everything we keep from a supplier document, whatever its type. */
public record DocumentBody(
		String tipoDocumento,
		SupplierParty supplier,
		String number,
		LocalDate date,
		BigDecimal total,
		List<DocumentLine> lines,
		List<VatSummary> summaries,
		List<DdtReference> ddtReferences) {
}
