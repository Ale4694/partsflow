package io.github.ale4694.partsflow.ai.provider;

import java.time.Duration;

/**
 * What went wrong with a call to the LLM provider, in terms that do not depend on which provider it is.
 * Each provider's {@link LlmErrorTranslator} turns its own exceptions into this; {@code LlmGateway} decides what
 * to do (retry, give up, which error to show) from {@link Kind} alone.
 *
 * @param kind what sort of failure it is
 * @param httpStatus the HTTP status of the provider's answer, 0 when there was none (network error)
 * @param providerStatus the provider's own status text (e.g. RESOURCE_EXHAUSTED, insufficient_quota), may be empty
 * @param providerMessage the provider's error message, for the server log
 * @param suggestedDelay how long the provider asks us to wait before trying again, or null
 */
public record LlmFailure(Kind kind, int httpStatus, String providerStatus, String providerMessage,
		Duration suggestedDelay) {

	public enum Kind {
		/** The request was refused: invalid key, model not found, bad request. Retrying cannot help. */
		REJECTED,
		/** The daily quota (or the credit) is used up. Retrying within a request cannot help. */
		DAILY_QUOTA,
		/** Too many requests for now; usually passes after a wait ({@code suggestedDelay}). */
		RATE_LIMITED,
		/** The provider is overloaded or failing (5xx): usually passes after a short wait. */
		OVERLOADED,
		/** No answer at all (connection problem). */
		NETWORK
	}
}
