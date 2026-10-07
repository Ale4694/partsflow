package io.github.ale4694.partsflow.invoiceimport.domain;

/** Who issued the document. {@code vatNumber} is the FatturaPA IdCodice, without the country prefix. */
public record SupplierParty(String vatNumber, String name) {
}
