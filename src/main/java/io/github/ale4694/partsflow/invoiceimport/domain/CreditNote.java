package io.github.ale4694.partsflow.invoiceimport.domain;

/** A supplier credit note (TD04): goods go back to the supplier, so quantities are reversed. */
public record CreditNote(DocumentBody body) implements Document {
}
