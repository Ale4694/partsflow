package io.github.ale4694.partsflow.ai.provider;

import java.util.Optional;

/**
 * The one thing the rest of the AI code needs to know about a provider's errors. There is one implementation per
 * provider (see {@code GeminiConfiguration} and {@code OpenAiCompatibleConfiguration}); the active one is a bean.
 */
public interface LlmErrorTranslator {

	/**
	 * @param error whatever the call to the model threw (the provider's exception may be nested in its causes)
	 * @return the failure, or empty when the error is not a provider error at all (a bug on our side, or an answer
	 *         we could not parse)
	 */
	Optional<LlmFailure> translate(Throwable error);
}
