package io.github.ale4694.partsflow.ai;

import com.google.genai.errors.ApiException;
import java.io.IOException;
import java.util.function.Supplier;
import tools.jackson.core.JacksonException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The only place that talks to the LLM. It adds what every call needs:
 * <ul>
 *   <li>a clear 503 when no API key is configured (the model is not called at all);</li>
 *   <li>a short, bounded retry with exponential backoff on rate limits (HTTP 429) and temporary server errors,
 *       then a 503 instead of a crash: the Gemini free tier has low limits;</li>
 *   <li>one INFO log line per call with the operation, outcome and duration, but never the document content;</li>
 *   <li>when the provider answers with an error, one WARN line with its HTTP status and error message, so a
 *       rejected request can be diagnosed (never the API key, headers or prompts).</li>
 * </ul>
 */
@Service
public class LlmGateway {

	private static final Logger log = LoggerFactory.getLogger(LlmGateway.class);

	/** Provider messages are short; the limit only protects the log from a huge error body. */
	private static final int MAX_PROVIDER_MESSAGE_CHARS = 500;

	private final ChatClient chatClient;
	private final boolean configured;
	private final AiProperties properties;

	public LlmGateway(ChatClient chatClient, @Value("${spring.ai.google.genai.api-key:}") String apiKey,
			AiProperties properties) {
		this.chatClient = chatClient;
		this.configured = !apiKey.isBlank();
		this.properties = properties;
	}

	public boolean isConfigured() {
		return configured;
	}

	/** Fails fast with a 503 when there is no API key, so callers can skip work that would be wasted. */
	public void requireConfigured() {
		if (!configured) {
			throw new AiUnavailableException(AiUnavailableException.Reason.KEY_MISSING,
					"AI features are disabled: the LLM_API_KEY environment variable is not set");
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
				ApiException apiError = find(ex, ApiException.class);
				boolean temporary = isTemporary(apiError) || (apiError == null && find(ex, IOException.class) != null);
				logCall(operation, attempt, apiError != null ? "http-" + apiError.code() : "error", started, promptChars);
				if (apiError != null) {
					logProviderError(operation, apiError);
				}
				if (apiError == null && !temporary) {
					if (find(ex, JacksonException.class) != null) {
						// The call worked but the answer is not the JSON we asked for: not a provider problem
						throw new LlmResponseException("Unusable LLM answer for " + operation, ex);
					}
					throw ex; // anything else is a bug on our side, let it surface as a 500
				}
				if (!temporary) {
					throw new AiUnavailableException(AiUnavailableException.Reason.REJECTED,
							"The LLM rejected the request (HTTP " + apiError.code()
									+ "). Check the API key and model configuration; the server log has the provider's message.",
							ex);
				}
				if (attempt >= retry.maxAttempts()) {
					throw new AiUnavailableException(AiUnavailableException.Reason.TEMPORARILY_UNAVAILABLE,
							"The LLM is rate limited or temporarily unavailable. Please try again in a minute.", ex);
				}
				sleep(backoffMillis);
				backoffMillis = Math.round(backoffMillis * retry.multiplier());
			}
		}
	}

	/** 429 = quota / rate limit; 5xx = provider trouble. Both usually pass if we wait a little. */
	private boolean isTemporary(ApiException error) {
		return error != null && (error.code() == 429 || error.code() >= 500);
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
	 * Gemini explains a rejection in the error body, e.g. status INVALID_ARGUMENT and which field is wrong.
	 * That text describes the request format, not the user's data, so it is safe to log at WARN.
	 */
	private void logProviderError(String operation, ApiException error) {
		log.warn("LLM provider error operation={} httpStatus={} providerStatus={} providerMessage={}", operation,
				error.code(), error.status(), abbreviate(error.message()));
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
