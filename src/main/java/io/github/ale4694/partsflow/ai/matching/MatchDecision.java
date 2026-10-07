package io.github.ale4694.partsflow.ai.matching;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** The LLM's answer for one invoice line. {@code itemId} must be one of the candidates, or null. */
public record MatchDecision(
		@JsonPropertyDescription("The id of the candidate that is the same product as the invoice line, or null if none clearly is")
		Long itemId,
		@JsonPropertyDescription("One or two short sentences explaining the choice")
		String justification) {
}
