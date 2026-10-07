package io.github.ale4694.partsflow.catalog;

public record SupplierItemCodeResponse(Long id, Long supplierId, String supplierCode, Long itemId, String itemCode) {

	static SupplierItemCodeResponse from(SupplierItemCode mapping) {
		return new SupplierItemCodeResponse(mapping.getId(), mapping.getSupplier().getId(),
				mapping.getSupplierCode(), mapping.getItem().getId(), mapping.getItem().getCode());
	}
}
