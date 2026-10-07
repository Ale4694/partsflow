package io.github.ale4694.partsflow.invoiceimport;

import io.github.ale4694.partsflow.invoiceimport.draft.DraftStatus;
import io.github.ale4694.partsflow.invoiceimport.draft.ImportDraft;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One row of the draft list: no lines, so listing stays cheap. */
public record DraftSummaryResponse(Long id, DraftStatus status, String supplierName, String tipoDocumento,
		String number, LocalDate date, BigDecimal total, Instant createdAt) {

	static DraftSummaryResponse from(ImportDraft draft) {
		return new DraftSummaryResponse(draft.getId(), draft.getStatus(), draft.getSupplier().getName(),
				draft.getTipoDocumento(), draft.getDocumentNumber(), draft.getDocumentDate(), draft.getTotalAmount(),
				draft.getCreatedAt());
	}
}
