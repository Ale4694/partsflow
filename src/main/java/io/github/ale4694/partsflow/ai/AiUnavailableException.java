package io.github.ale4694.partsflow.ai;

import java.time.Duration;

/** The LLM cannot be used right now (no API key, request rejected, quota exhausted, provider down). Mapped to HTTP 503. */
public class AiUnavailableException extends RuntimeException {

	/** Why, as a stable code clients can rely on (the message text is for people and may change). */
	public enum Reason {
		/** LLM_API_KEY is not set on the server. */
		KEY_MISSING("AI_KEY_MISSING"),
		/** The provider refused the request (HTTP 4xx other than a rate limit): key or configuration problem. */
		REJECTED("AI_REJECTED"),
		/** The daily quota is used up (free tier: about 20 requests per day per model). Retrying cannot help. */
		DAILY_QUOTA_EXHAUSTED("AI_DAILY_QUOTA_EXHAUSTED"),
		/** Rate limited, and the provider asks for a wait too long to hold the request for. */
		RATE_LIMITED("AI_RATE_LIMITED"),
		/** Provider trouble or a short rate limit that did not pass within our few retries. */
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
	private final Duration retryAfter;

	public AiUnavailableException(Reason reason, String message) {
		this(reason, message, null, null);
	}

	public AiUnavailableException(Reason reason, String message, Throwable cause) {
		this(reason, message, null, cause);
	}

	/** @param retryAfter how long the provider asked us to wait, or null when unknown */
	public AiUnavailableException(Reason reason, String message, Duration retryAfter, Throwable cause) {
		super(message, cause);
		this.reason = reason;
		this.retryAfter = retryAfter;
	}

	public Reason reason() {
		return reason;
	}

	/** The wait the provider suggested, or null. */
	public Duration retryAfter() {
		return retryAfter;
	}
}
