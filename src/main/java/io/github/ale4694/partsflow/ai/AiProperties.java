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
		@DefaultValue Retry retry) {

	/** Retry for rate-limit (HTTP 429) and temporary server errors. */
	public record Retry(
			@DefaultValue("3") int maxAttempts,
			@DefaultValue("2s") Duration initialBackoff,
			@DefaultValue("2.0") double multiplier) {
	}
}
