package io.github.ale4694.partsflow.ai;

/** The LLM answered, but not in a form we can use (e.g. not valid JSON). Mapped to HTTP 502. */
public class LlmResponseException extends RuntimeException {

	public LlmResponseException(String message, Throwable cause) {
		super(message, cause);
	}
}
