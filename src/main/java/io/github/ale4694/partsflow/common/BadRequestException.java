package io.github.ale4694.partsflow.common;

/** The request is wrong in a way the client can fix (for example an unknown search mode). Mapped to HTTP 400. */
public class BadRequestException extends RuntimeException {

	public BadRequestException(String message) {
		super(message);
	}
}
