package io.github.ale4694.partsflow.ai;

/** The LLM cannot be used right now (no API key, request rejected, quota exhausted, provider down). Mapped to HTTP 503. */
public class AiUnavailableException extends RuntimeException {

	/** Why, as a stable code clients can rely on (the message text is for people and may change). */
	public enum Reason {
		/** LLM_API_KEY is not set on the server. */
		KEY_MISSING("AI_KEY_MISSING"),
		/** The provider refused the request (HTTP 4xx other than a rate limit): key or configuration problem. */
		REJECTED("AI_REJECTED"),
		/** Rate limit, exhausted quota or provider trouble: it usually passes after a short wait. */
		TEMPORARILY_UNAVAILABLE("AI_TEMPORARILY_UNAVAILABLE");

		private final String code;

		Reason(String code) {
			this.code = code;
		}

		public String code() {
			return code;
		}
	}

	private final Reason reason;

	public AiUnavailableException(Reason reason, String message) {
		super(message);
		this.reason = reason;
	}

	public AiUnavailableException(Reason reason, String message, Throwable cause) {
		super(message, cause);
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}
}
