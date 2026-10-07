package io.github.ale4694.partsflow.invoiceimport;

import jakarta.validation.constraints.NotNull;

public record ResolveLineRequest(@NotNull Long itemId) {
}
