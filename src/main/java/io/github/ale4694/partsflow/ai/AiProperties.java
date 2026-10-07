package io.github.ale4694.partsflow.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Tunables for the AI features, under {@code partsflow.ai} in application.yml. */
@ConfigurationProperties("partsflow.ai")
public record AiProperties(
		/** Most tool calls the assistant may make while answering one question. */
		@DefaultValue("5") int maxAgentSteps,
		/** Candidate items given to the LLM when matching one invoice line. */
		@DefaultValue("5") int maxMatchCandidates,
		/** Candidates with a lower pg_trgm similarity (0 to 1) are not shown to the LLM. */
		@DefaultValue("0.1") double minSimilarity,
		/** Most pending lines the LLM is asked about in one request (free-tier friendly). */
		@DefaultValue("10") int maxLinesPerMatchRequest,
		/** PDF text longer than this is rejected instead of being sent to the LLM. */
		@DefaultValue("30000") int maxPdfTextChars,
		@DefaultValue Retry retry,
		/** Which LLM service is used (LLM_PROVIDER): gemini or openai-compatible. */
		@DefaultValue("gemini") Provider provider,
		/** The API key (LLM_API_KEY). Read only from the environment. */
		@DefaultValue("") String apiKey,
		/** Address of the OpenAI-compatible service (LLM_BASE_URL). Not used by Gemini. */
		@DefaultValue("") String baseUrl,
		/** Model name (LLM_MODEL). Gemini has a default; an OpenAI-compatible service needs one. */
		@DefaultValue("") String model) {

	public enum Provider {
		GEMINI,
		OPENAI_COMPATIBLE
	}

	/**
	 * True when the settings are complete enough to call the LLM. Gemini needs only a key (the model has a
	 * default); an OpenAI-compatible service also needs its address and a model name. Incomplete settings never
	 * stop the application from starting: the AI endpoints answer 503 and everything else works.
	 */
	public boolean configured() {
		if (apiKey.isBlank()) {
			return false;
		}
		return provider == Provider.GEMINI || (!baseUrl.isBlank() && !model.isBlank());
	}

	/** Retry for rate-limit (HTTP 429) and temporary server errors. */
	public record Retry(
			@DefaultValue("3") int maxAttempts,
			@DefaultValue("2s") Duration initialBackoff,
			@DefaultValue("2.0") double multiplier,
			/** A 429 that asks us to wait longer than this is not retried: the user gets the answer at once. */
			@DefaultValue("20s") Duration maxSuggestedDelay) {
	}
}
