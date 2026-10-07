package io.github.ale4694.partsflow.invoiceimport.domain;

import java.time.LocalDate;

/** DatiDDT: the delivery note (documento di trasporto) the invoice refers to. */
public record DdtReference(String number, LocalDate date) {
}
