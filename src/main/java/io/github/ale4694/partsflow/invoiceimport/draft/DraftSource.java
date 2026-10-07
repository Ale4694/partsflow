package io.github.ale4694.partsflow.invoiceimport.draft;

public enum DraftSource {
	/** Read by deterministic code from a FatturaPA XML file. */
	XML,
	/** Extracted by an LLM from a PDF: review more carefully. */
	PDF
}
