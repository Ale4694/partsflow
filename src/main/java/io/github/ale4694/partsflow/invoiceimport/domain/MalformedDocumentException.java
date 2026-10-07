package io.github.ale4694.partsflow.invoiceimport.domain;

/**
 * The file is not a readable document: broken XML, a missing required element, an invalid number or date.
 * Mapped to HTTP 400.
 */
public class MalformedDocumentException extends RuntimeException {

	public MalformedDocumentException(String message) {
		super(message);
	}

	public MalformedDocumentException(String message, Throwable cause) {
		super(message, cause);
	}
}
