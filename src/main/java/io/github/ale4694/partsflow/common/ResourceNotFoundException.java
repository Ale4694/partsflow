package io.github.ale4694.partsflow.common;

/** The requested resource does not exist. Mapped to HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException(String resource, Object id) {
		super(resource + " " + id + " not found");
	}
}
