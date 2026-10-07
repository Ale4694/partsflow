package io.github.ale4694.partsflow.common;

/** The request conflicts with the current state of the data (duplicate, still referenced...). Mapped to HTTP 409. */
public class ConflictException extends RuntimeException {

	public ConflictException(String message) {
		super(message);
	}
}
