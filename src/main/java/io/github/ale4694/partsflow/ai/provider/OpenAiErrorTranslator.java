package io.github.ale4694.partsflow.ai.provider;

import com.openai.core.http.Headers;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import io.github.ale4694.partsflow.ai.provider.LlmFailure.Kind;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Reads the exceptions of the official OpenAI Java SDK, which Spring AI uses for every OpenAI-compatible service
 * (OpenAI, Mistral, Groq, DeepSeek, OpenRouter...). Those services share the status codes and the error body
 * shape, but not the wording of their quota messages, so daily limits are recognised by several hints.
 */
public class OpenAiErrorTranslator implements LlmErrorTranslator {

	@Override
	public Optional<LlmFailure> translate(Throwable error) {
		OpenAIServiceException service = ProviderErrors.find(error, OpenAIServiceException.class);
		if (service != null) {
			return Optional.of(classify(service));
		}
		if (ProviderErrors.find(error, OpenAIIoException.class) != null) {
			return Optional.of(new LlmFailure(Kind.NETWORK, 0, "", String.valueOf(error.getMessage()), null));
		}
		return Optional.empty();
	}

	private LlmFailure classify(OpenAIServiceException error) {
		int status = error.statusCode();
		String message = error.getMessage() == null ? "" : error.getMessage();
		String code = error.code().orElse("");
		String providerStatus = code.isEmpty() ? error.type().orElse("") : code;
		Duration headerDelay = retryAfter(error.headers());

		// 402: the account has no credit left (e.g. OpenRouter). insufficient_quota: the same on OpenAI.
		if (status == 402 || "insufficient_quota".equals(code)) {
			return new LlmFailure(Kind.DAILY_QUOTA, status, providerStatus, message, headerDelay);
		}
		if (status == 429) {
			QuotaAdvice advice = QuotaAdvice.from(message);
			Duration delay = headerDelay != null ? headerDelay : advice.suggestedDelay();
			boolean daily = advice.daily() || (headerDelay != null && headerDelay.compareTo(Duration.ofHours(1)) >= 0);
			return new LlmFailure(daily ? Kind.DAILY_QUOTA : Kind.RATE_LIMITED, status, providerStatus, message, delay);
		}
		// 408 timeout, 500/502/503/504, and 529 "overloaded" used by some providers
		if (status == 408 || status >= 500) {
			return new LlmFailure(Kind.OVERLOADED, status, providerStatus, message, null);
		}
		// 400 bad request, 401 invalid key, 403, 404 model not found, 422...
		return new LlmFailure(Kind.REJECTED, status, providerStatus, message, null);
	}

	/** The standard Retry-After header (seconds), or the "retry-after-ms" variant some providers send. */
	private static Duration retryAfter(Headers headers) {
		Duration seconds = firstNumber(headers.values("retry-after"), 1000);
		return seconds != null ? seconds : firstNumber(headers.values("retry-after-ms"), 1);
	}

	private static Duration firstNumber(List<String> values, long millisPerUnit) {
		for (String value : values) {
			try {
				return Duration.ofMillis(Math.round(Double.parseDouble(value.trim()) * millisPerUnit));
			}
			catch (NumberFormatException ex) {
				// Retry-After may also be an HTTP date: not worth parsing, we fall back to our own backoff
			}
		}
		return null;
	}
}
