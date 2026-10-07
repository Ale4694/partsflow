package io.github.ale4694.partsflow.catalog;

public record SupplierResponse(Long id, String name, String vatNumber) {

	static SupplierResponse from(Supplier supplier) {
		return new SupplierResponse(supplier.getId(), supplier.getName(), supplier.getVatNumber());
	}
}
