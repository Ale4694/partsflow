package io.github.ale4694.partsflow.invoiceimport.domain;

/**
 * The document is readable but breaks a business rule: unsupported document type, totals that do not add up,
 * unknown supplier... Mapped to HTTP 422.
 */
public class InvalidDocumentException extends RuntimeException {

	public InvalidDocumentException(String message) {
		super(message);
	}
}
