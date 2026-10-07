package io.github.ale4694.partsflow.invoiceimport;

import io.github.ale4694.partsflow.invoiceimport.draft.DraftStatus;
import io.github.ale4694.partsflow.invoiceimport.draft.ImportDraft;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** A draft with all its lines: what the user reviews before confirming. */
public record DraftResponse(
		Long id,
		DraftStatus status,
		Long supplierId,
		String supplierName,
		String tipoDocumento,
		String number,
		LocalDate date,
		BigDecimal total,
		Instant createdAt,
		Instant confirmedAt,
		List<DdtReferenceResponse> ddtReferences,
		long pendingLines,
		List<DraftLineResponse> lines) {

	public record DdtReferenceResponse(String number, LocalDate date) {
	}

	static DraftResponse from(ImportDraft draft) {
		return new DraftResponse(draft.getId(), draft.getStatus(), draft.getSupplier().getId(),
				draft.getSupplier().getName(), draft.getTipoDocumento(), draft.getDocumentNumber(),
				draft.getDocumentDate(), draft.getTotalAmount(), draft.getCreatedAt(), draft.getConfirmedAt(),
				draft.getDdtReferences().stream().map(d -> new DdtReferenceResponse(d.getNumber(), d.getDate())).toList(),
				draft.pendingLineCount(),
				draft.getLines().stream().map(DraftLineResponse::from).toList());
	}
}
