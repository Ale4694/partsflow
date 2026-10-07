package io.github.ale4694.partsflow.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SupplierItemCodeRequest(
		@NotBlank @Size(max = 100) String supplierCode,
		@NotNull Long itemId) {
}
