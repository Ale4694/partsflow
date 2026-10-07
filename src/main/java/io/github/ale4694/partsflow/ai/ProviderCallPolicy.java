package io.github.ale4694.partsflow.ai;

import io.github.ale4694.partsflow.ai.provider.LlmErrorTranslator;
import io.github.ale4694.partsflow.ai.provider.LlmFailure;
import io.github.ale4694.partsflow.ai.provider.QuotaAdvice;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

/**
 * What every call to the AI provider needs, whichever provider it is and whether it is a chat call or an
 * embedding call. Used by {@link LlmGateway} (chat) and by the embedding gateway, so that both behave the same:
 * <ul>
 *   <li>a short, bounded retry with exponential backoff, only for errors that pass quickly (provider overloaded,
 *       a rate limit with a short wait); a daily quota or a long wait fails at once with a 503 that says so;</li>
 *   <li>one INFO log line per attempt with the operation, outcome, duration and the size of the request, but never
 *       the content;</li>
 *   <li>when the provider answers with an error, one WARN line with its HTTP status and error message, so a
 *       rejected request can be diagnosed (never the API key, headers or prompts).</li>
 * </ul>
 * It knows nothing about Gemini or OpenAI: provider exceptions arrive as {@link LlmFailure} through the
 * {@link LlmErrorTranslator} of the active provider.
 */
@Component
public class ProviderCallPolicy {

	private static final Logger log = LoggerFactory.getLogger(ProviderCallPolicy.class);

	/** Provider messages are short; the limit only protects the log from a huge error body. */
	private static final int MAX_PROVIDER_MESSAGE_CHARS = 500;

	private final AiProperties properties;
	private final LlmErrorTranslator translator;

	public ProviderCallPolicy(AiProperties properties, LlmErrorTranslator translator) {
		this.properties = properties;
		this.translator = translator;
	}

	/**
	 * @param operation a short name for the logs, e.g. "inventory-assistant" or "item-embedding"
	 * @param size what is logged about the size of the request, e.g. "promptChars=783" or "texts=100"
	 * @param action the call itself
	 */
	public <T> T execute(String operation, String size, Supplier<T> action) {
		AiProperties.Retry retry = properties.retry();
		long backoffMillis = retry.initialBackoff().toMillis();
		for (int attempt = 1; ; attempt++) {
			long started = System.nanoTime();
			try {
				T result = action.get();
				logCall(operation, attempt, "ok", started, size);
				return result;
			}
			catch (RuntimeException ex) {
				Optional<LlmFailure> translated = translator.translate(ex);
				boolean providerError = translated.isPresent() && translated.get().httpStatus() > 0;
				logCall(operation, attempt, providerError ? "http-" + translated.get().httpStatus() : "error", started,
						size);
				if (translated.isEmpty()) {
					if (find(ex, JacksonException.class) != null) {
						// The call worked but the answer is not the JSON we asked for: not a provider problem
						throw new LlmResponseException("Unusable LLM answer for " + operation, ex);
					}
					throw ex; // anything else is a bug on our side, let it surface as a 500
				}
				LlmFailure failure = translated.get();
				if (providerError) {
					logProviderError(operation, failure);
				}
				long waitMillis = backoffMillis;
				switch (failure.kind()) {
					case REJECTED -> throw new AiUnavailableException(AiUnavailableException.Reason.REJECTED,
							"The LLM rejected the request (HTTP " + failure.httpStatus()
									+ "). Check the API key and model configuration; the server log has the provider's message.",
							ex);
					case DAILY_QUOTA -> throw dailyQuota(failure, ex);
					case RATE_LIMITED -> {
						// Worth a retry only when the wait is short; otherwise the user gets the answer at once
						Duration delay = failure.suggestedDelay();
						if (delay != null && delay.compareTo(retry.maxSuggestedDelay()) > 0) {
							throw new AiUnavailableException(AiUnavailableException.Reason.RATE_LIMITED,
									"The AI service is rate limited. Try again in about " + QuotaAdvice.describe(delay) + ".",
									delay, ex);
						}
						if (delay != null) {
							waitMillis = Math.max(backoffMillis, delay.toMillis());
						}
					}
					case OVERLOADED, NETWORK -> {
						// temporary: retry with the plain backoff
					}
				}
				if (attempt >= retry.maxAttempts()) {
					throw new AiUnavailableException(AiUnavailableException.Reason.TEMPORARILY_UNAVAILABLE,
							"The LLM is rate limited or temporarily unavailable. Please try again in a minute.", ex);
				}
				sleep(waitMillis);
				backoffMillis = Math.round(backoffMillis * retry.multiplier());
			}
		}
	}

	/** The daily quota (or the credit) is used up: nothing we do inside this request can fix it. */
	private AiUnavailableException dailyQuota(LlmFailure failure, RuntimeException cause) {
		Duration delay = failure.suggestedDelay();
		String wait = delay == null ? "" : " Try again in about " + QuotaAdvice.describe(delay) + ".";
		return new AiUnavailableException(AiUnavailableException.Reason.DAILY_QUOTA_EXHAUSTED,
				"The quota or credit of the AI service is exhausted." + wait, delay, cause);
	}

	private void sleep(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new AiUnavailableException(AiUnavailableException.Reason.TEMPORARILY_UNAVAILABLE,
					"Interrupted while waiting to retry the LLM call", ex);
		}
	}

	private void logCall(String operation, int attempt, String outcome, long startedNanos, String size) {
		log.info("LLM call operation={} attempt={} outcome={} durationMs={} {}", operation, attempt, outcome,
				(System.nanoTime() - startedNanos) / 1_000_000, size);
	}

	/**
	 * Providers explain a rejection in the error body, e.g. which field is wrong or which quota is used up.
	 * That text describes the request format, not the user's data, so it is safe to log at WARN.
	 */
	private void logProviderError(String operation, LlmFailure failure) {
		log.warn("LLM provider error operation={} httpStatus={} providerStatus={} providerMessage={}", operation,
				failure.httpStatus(), failure.providerStatus(), abbreviate(failure.providerMessage()));
	}

	private static String abbreviate(String text) {
		if (text == null) {
			return "";
		}
		String oneLine = text.replaceAll("\\s+", " ").strip();
		return oneLine.length() <= MAX_PROVIDER_MESSAGE_CHARS ? oneLine
				: oneLine.substring(0, MAX_PROVIDER_MESSAGE_CHARS) + "...";
	}

	private static <E extends Throwable> E find(Throwable error, Class<E> type) {
		for (Throwable current = error; current != null; current = current.getCause()) {
			if (type.isInstance(current)) {
				return type.cast(current);
			}
		}
		return null;
	}
}
