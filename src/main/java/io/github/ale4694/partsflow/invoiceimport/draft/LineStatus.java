package io.github.ale4694.partsflow.invoiceimport.draft;

public enum LineStatus {
	/** The supplier code maps to one of our items: this line will change stock on confirmation. */
	MATCHED,
	/** Unknown or missing supplier code: a person must pick the item or skip the line. */
	PENDING_REVIEW,
	/** Does not touch stock (service line without quantity, or skipped by the user). */
	SKIPPED
}
