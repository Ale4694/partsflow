package io.github.ale4694.partsflow.ai.assistant;

/** The answer, and how many read-only tool calls it took to produce it. */
public record AssistantResponse(String answer, int toolCalls) {
}
