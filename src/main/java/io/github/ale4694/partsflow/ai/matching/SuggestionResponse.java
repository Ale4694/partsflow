package io.github.ale4694.partsflow.ai.matching;

import java.time.Instant;

public record SuggestionResponse(Long id, Long draftId, Long draftLineId, Long itemId, String itemCode,
		String itemDescription, String justification, SuggestionStatus status, Instant createdAt) {
}
