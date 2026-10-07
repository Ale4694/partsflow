package io.github.ale4694.partsflow.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ItemRequest(
		@NotBlank @Size(max = 50) String code,
		@NotBlank @Size(max = 500) String description,
		@NotBlank @Size(max = 10) String unit,
		@NotNull @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal reorderThreshold) {
}
