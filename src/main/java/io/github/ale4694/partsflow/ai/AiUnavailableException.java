package io.github.ale4694.partsflow.ai;

/** The LLM cannot be used right now (no API key, quota exhausted, provider down). Mapped to HTTP 503. */
public class AiUnavailableException extends RuntimeException {

	public AiUnavailableException(String message) {
		super(message);
	}

	public AiUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
