package io.github.ale4694.partsflow.invoiceimport.domain;

/**
 * A supplier document. Sealed so the compiler knows every possible type: a {@code switch} over a Document
 * without a default branch stops compiling if someone adds a new type and forgets to handle it.
 */
public sealed interface Document permits Invoice, CreditNote {

	DocumentBody body();
}
