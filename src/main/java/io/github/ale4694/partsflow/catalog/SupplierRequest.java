package io.github.ale4694.partsflow.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SupplierRequest(
		@NotBlank @Size(max = 200) String name,
		// FatturaPA IdCodice: letters and digits, upper case, without the country prefix
		@NotBlank @Pattern(regexp = "[A-Z0-9]{8,28}", message = "must be 8-28 upper case letters or digits") String vatNumber) {
}
