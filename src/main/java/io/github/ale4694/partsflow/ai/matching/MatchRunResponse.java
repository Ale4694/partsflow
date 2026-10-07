package io.github.ale4694.partsflow.ai.matching;

import java.util.List;

/** Result of asking for suggestions: what was produced, and how many pending lines were left for a later request. */
public record MatchRunResponse(List<SuggestionResponse> suggestions, int linesLeftForNextRequest) {
}
