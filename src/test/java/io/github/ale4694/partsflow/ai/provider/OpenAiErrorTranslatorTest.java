package io.github.ale4694.partsflow.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.openai.core.http.Headers;
import com.openai.errors.BadRequestException;
import com.openai.errors.InternalServerException;
import com.openai.errors.NotFoundException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import com.openai.errors.UnexpectedStatusCodeException;
import com.openai.models.ErrorObject;
import io.github.ale4694.partsflow.ai.provider.LlmFailure.Kind;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Errors of the OpenAI Java SDK (used for OpenAI, Mistral, Groq, DeepSeek, OpenRouter...), built by hand. */
class OpenAiErrorTranslatorTest {

	private final OpenAiErrorTranslator translator = new OpenAiErrorTranslator();

	private static ErrorObject body(String message, String code) {
		return ErrorObject.builder().message(message).type("error").code(Optional.ofNullable(code))
				.param(Optional.empty()).build();
	}

	private static Headers retryAfter(String seconds) {
		return Headers.builder().put("retry-after", seconds).build();
	}

	private LlmFailure translate(Throwable error) {
		return translator.translate(error).orElseThrow();
	}

	@Test
	void anInvalidKeyIsRejected() {
		LlmFailure failure = translate(UnauthorizedException.builder().error(body("Incorrect API key provided", "invalid_api_key"))
				.headers(Headers.builder().build()).build());

		assertThat(failure.kind()).isEqualTo(Kind.REJECTED);
		assertThat(failure.httpStatus()).isEqualTo(401);
		assertThat(failure.providerStatus()).isEqualTo("invalid_api_key");
		assertThat(failure.providerMessage()).contains("Incorrect API key");
	}

	@Test
	void aModelThatDoesNotExistIsRejected() {
		LlmFailure failure = translate(NotFoundException.builder().error(body("The model `x` does not exist", "model_not_found"))
				.headers(Headers.builder().build()).build());

		assertThat(failure.kind()).isEqualTo(Kind.REJECTED);
		assertThat(failure.httpStatus()).isEqualTo(404);
	}

	@Test
	void aBadRequestIsRejected() {
		LlmFailure failure = translate(BadRequestException.builder().error(body("Unsupported parameter", null))
				.headers(Headers.builder().build()).build());

		assertThat(failure.kind()).isEqualTo(Kind.REJECTED);
		assertThat(failure.httpStatus()).isEqualTo(400);
	}

	@Test
	void aRateLimitWithARetryAfterHeaderCarriesTheDelay() {
		LlmFailure failure = translate(RateLimitException.builder().error(body("Rate limit reached for requests", "rate_limit_exceeded"))
				.headers(retryAfter("30")).build());

		assertThat(failure.kind()).isEqualTo(Kind.RATE_LIMITED);
		assertThat(failure.httpStatus()).isEqualTo(429);
		assertThat(failure.suggestedDelay()).isEqualTo(Duration.ofSeconds(30));
	}

	@Test
	void aRateLimitWithoutAnyHintHasNoDelay() {
		LlmFailure failure = translate(RateLimitException.builder().error(body("Too many requests", null))
				.headers(Headers.builder().build()).build());

		assertThat(failure.kind()).isEqualTo(Kind.RATE_LIMITED);
		assertThat(failure.suggestedDelay()).isNull();
	}

	@Test
	void aRetryInMillisecondsHeaderIsUnderstood() {
		LlmFailure failure = translate(RateLimitException.builder().error(body("Slow down", null))
				.headers(Headers.builder().put("retry-after-ms", "1500").build()).build());

		assertThat(failure.suggestedDelay()).isEqualTo(Duration.ofMillis(1500));
	}

	@Test
	void outOfCreditIsTheQuotaError() {
		LlmFailure failure = translate(RateLimitException.builder()
				.error(body("You exceeded your current quota, please check your plan and billing details.", "insufficient_quota"))
				.headers(Headers.builder().build()).build());

		assertThat(failure.kind()).isEqualTo(Kind.DAILY_QUOTA);
	}

	@Test
	void paymentRequiredIsTheQuotaError() {
		LlmFailure failure = translate(UnexpectedStatusCodeException.builder().statusCode(402)
				.error(body("Insufficient credits", null)).headers(Headers.builder().build()).build());

		assertThat(failure.kind()).isEqualTo(Kind.DAILY_QUOTA);
		assertThat(failure.httpStatus()).isEqualTo(402);
	}

	@Test
	void aPerDayLimitInTheMessageIsTheQuotaErrorWithItsDelay() {
		LlmFailure failure = translate(RateLimitException.builder().error(body(
				"Rate limit reached for model on tokens per day (TPD): Limit 100000, Used 99990. Please try again in 7m12s.",
				"rate_limit_exceeded")).headers(Headers.builder().build()).build());

		assertThat(failure.kind()).isEqualTo(Kind.DAILY_QUOTA);
		assertThat(failure.suggestedDelay()).isEqualTo(Duration.ofMinutes(7).plusSeconds(12));
	}

	@Test
	void aRetryAfterOfHoursIsTheQuotaError() {
		LlmFailure failure = translate(RateLimitException.builder().error(body("Rate limit reached", null))
				.headers(retryAfter("7200")).build());

		assertThat(failure.kind()).isEqualTo(Kind.DAILY_QUOTA);
		assertThat(failure.suggestedDelay()).isEqualTo(Duration.ofHours(2));
	}

	@Test
	void serverErrorsAndOverloadAreTemporary() {
		LlmFailure unavailable = translate(InternalServerException.builder().statusCode(503)
				.error(body("The model is overloaded", null)).headers(Headers.builder().build()).build());
		LlmFailure overloaded = translate(UnexpectedStatusCodeException.builder().statusCode(529)
				.error(body("Overloaded", null)).headers(Headers.builder().build()).build());

		assertThat(unavailable.kind()).isEqualTo(Kind.OVERLOADED);
		assertThat(unavailable.httpStatus()).isEqualTo(503);
		assertThat(overloaded.kind()).isEqualTo(Kind.OVERLOADED);
	}

	@Test
	void aConnectionProblemIsANetworkFailureWithoutStatus() {
		LlmFailure failure = translate(new OpenAIIoException("connection refused"));

		assertThat(failure.kind()).isEqualTo(Kind.NETWORK);
		assertThat(failure.httpStatus()).isZero();
	}

	@Test
	void findsTheProviderErrorInsideAWrappingException() {
		RuntimeException wrapped = new RuntimeException("call failed", UnauthorizedException.builder()
				.error(body("bad key", "invalid_api_key")).headers(Headers.builder().build()).build());

		assertThat(translate(wrapped).kind()).isEqualTo(Kind.REJECTED);
	}

	@Test
	void otherExceptionsAreNotProviderErrors() {
		assertThat(translator.translate(new IllegalStateException("a bug of ours"))).isEmpty();
	}
}
