package io.github.ale4694.partsflow.invoiceimport.domain;

/** FatturaPA CodiceArticolo: a typed code (EAN, supplier SKU...) printed on an invoice line. */
public record ArticleCode(String type, String value) {
}
