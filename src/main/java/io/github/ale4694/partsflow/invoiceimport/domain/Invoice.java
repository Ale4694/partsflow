package io.github.ale4694.partsflow.invoiceimport.domain;

/** A normal supplier invoice (TD01, TD24): goods come into stock. */
public record Invoice(DocumentBody body) implements Document {
}
