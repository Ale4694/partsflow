package io.github.ale4694.partsflow.invoiceimport.domain;

/** A supplier delivery note (DDT): goods come into stock. Usually read from a PDF; prices may be missing. */
public record DeliveryNote(DocumentBody body) implements Document {
}
