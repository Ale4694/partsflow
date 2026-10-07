package io.github.ale4694.partsflow.invoiceimport;

import io.github.ale4694.partsflow.invoiceimport.draft.ImportDraftLine;
import io.github.ale4694.partsflow.invoiceimport.draft.LineStatus;
import java.math.BigDecimal;

public record DraftLineResponse(
		Long id,
		int lineNumber,
		String supplierCode,
		String description,
		BigDecimal quantity,
		String unit,
		BigDecimal unitPrice,
		BigDecimal totalPrice,
		BigDecimal vatRate,
		BigDecimal stockDelta,
		LineStatus status,
		Long itemId,
		String itemCode) {

	static DraftLineResponse from(ImportDraftLine line) {
		return new DraftLineResponse(line.getId(), line.getLineNumber(), line.getSupplierCode(), line.getDescription(),
				line.getQuantity(), line.getUnit(), line.getUnitPrice(), line.getTotalPrice(), line.getVatRate(),
				line.getStockDelta(), line.getStatus(),
				line.getItem() == null ? null : line.getItem().getId(),
				line.getItem() == null ? null : line.getItem().getCode());
	}
}
