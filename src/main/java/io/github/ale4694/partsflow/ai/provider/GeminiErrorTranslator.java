package io.github.ale4694.partsflow.ai.provider;

import com.google.genai.errors.ApiException;
import io.github.ale4694.partsflow.ai.provider.LlmFailure.Kind;
import java.io.IOException;
import java.util.Optional;

/** Reads the exceptions of the Google GenAI SDK (Gemini). */
public class GeminiErrorTranslator implements LlmErrorTranslator {

	@Override
	public Optional<LlmFailure> translate(Throwable error) {
		ApiException api = ProviderErrors.find(error, ApiException.class);
		if (api != null) {
			return Optional.of(classify(api));
		}
		if (ProviderErrors.find(error, IOException.class) != null) {
			return Optional.of(new LlmFailure(Kind.NETWORK, 0, "", String.valueOf(error.getMessage()), null));
		}
		return Optional.empty();
	}

	private LlmFailure classify(ApiException api) {
		int status = api.code();
		String message = api.message() == null ? "" : api.message();
		String providerStatus = api.status() == null ? "" : api.status();
		if (status == 429) {
			QuotaAdvice advice = QuotaAdvice.from(message);
			Kind kind = advice.daily() ? Kind.DAILY_QUOTA : Kind.RATE_LIMITED;
			return new LlmFailure(kind, status, providerStatus, message, advice.suggestedDelay());
		}
		// 5xx, notably 503 "This model is currently experiencing high demand"
		Kind kind = status >= 500 ? Kind.OVERLOADED : Kind.REJECTED;
		return new LlmFailure(kind, status, providerStatus, message, null);
	}
}
