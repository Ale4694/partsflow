package io.github.ale4694.partsflow.invoiceimport.draft;

public enum DraftStatus {
	/** Waiting for the user: lines can still be resolved or skipped. */
	DRAFT,
	/** Confirmed: the stock movements were written. Final. */
	CONFIRMED
}
