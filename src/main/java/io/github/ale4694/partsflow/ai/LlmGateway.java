package io.github.ale4694.partsflow.ai;

import io.github.ale4694.partsflow.ai.provider.LlmErrorTranslator;
import io.github.ale4694.partsflow.ai.provider.LlmFailure;
import io.github.ale4694.partsflow.ai.provider.QuotaAdvice;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;

/**
 * The only place that talks to the LLM, whichever provider is active. It adds what every call needs:
 * <ul>
 *   <li>a clear 503 when the AI is not configured (the model is not called at all);</li>
 *   <li>a short, bounded retry with exponential backoff, only for errors that pass quickly (provider overloaded,
 *       a rate limit with a short wait); a daily quota or a long wait fails at once with a 503 that says so;</li>
 *   <li>one INFO log line per call with the operation, outcome and duration, but never the document content;</li>
 *   <li>when the provider answers with an error, one WARN line with its HTTP status and error message, so a
 *       rejected request can be diagnosed (never the API key, headers or prompts).</li>
 * </ul>
 * It knows nothing about Gemini or OpenAI: provider exceptions arrive as {@link LlmFailure} through the
 * {@link LlmErrorTranslator} of the active provider.
 */
@Service
public class LlmGateway {

	private static final Logger log = LoggerFactory.getLogger(LlmGateway.class);

	/** Provider messages are short; the limit only protects the log from a huge error body. */
	private static final int MAX_PROVIDER_MESSAGE_CHARS = 500;

	private final ChatClient chatClient;
	private final AiProperties properties;
	private final LlmErrorTranslator translator;

	public LlmGateway(ChatClient chatClient, AiProperties properties, LlmErrorTranslator translator) {
		this.chatClient = chatClient;
		this.properties = properties;
		this.translator = translator;
	}

	public boolean isConfigured() {
		return properties.configured();
	}

	/** Fails fast with a 503 when the AI is not configured, so callers can skip work that would be wasted. */
	public void requireConfigured() {
		if (!isConfigured()) {
			throw new AiUnavailableException(AiUnavailableException.Reason.KEY_MISSING,
					"AI features are disabled: LLM_API_KEY is not set"
							+ (properties.provider() == AiProperties.Provider.OPENAI_COMPATIBLE
									? " or LLM_BASE_URL / LLM_MODEL are missing (provider openai-compatible)" : ""));
		}
	}

	/** Asks the LLM for an answer shaped like {@code type} (JSON mapped to a record). The caller must validate it. */
	public <T> T structured(String operation, String systemPrompt, String userPrompt, Class<T> type) {
		return call(operation, systemPrompt.length() + userPrompt.length(), () -> chatClient.prompt()
				.system(systemPrompt)
				.user(userPrompt)
				.call()
				.entity(type));
	}

	/** Asks the LLM a question it may answer by calling the given {@code @Tool} methods. */
	public String converse(String operation, String systemPrompt, String userPrompt, Object tools) {
		return call(operation, systemPrompt.length() + userPrompt.length(), () -> chatClient.prompt()
				.system(systemPrompt)
				.user(userPrompt)
				.tools(tools)
				.call()
				.content());
	}

	private <T> T call(String operation, int promptChars, Supplier<T> action) {
		requireConfigured();
		AiProperties.Retry retry = properties.retry();
		long backoffMillis = retry.initialBackoff().toMillis();
		for (int attempt = 1; ; attempt++) {
			long started = System.nanoTime();
			try {
				T result = action.get();
				logCall(operation, attempt, "ok", started, promptChars);
				return result;
			}
			catch (RuntimeException ex) {
				Optional<LlmFailure> translated = translator.translate(ex);
				boolean providerError = translated.isPresent() && translated.get().httpStatus() > 0;
				logCall(operation, attempt, providerError ? "http-" + translated.get().httpStatus() : "error", started,
						promptChars);
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
				"The quota or credit of the AI service is exhausted." + wait,
				delay, cause);
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

	private void logCall(String operation, int attempt, String outcome, long startedNanos, int promptChars) {
		log.info("LLM call operation={} attempt={} outcome={} durationMs={} promptChars={}", operation, attempt, outcome,
				(System.nanoTime() - startedNanos) / 1_000_000, promptChars);
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
