package io.github.ale4694.partsflow.ai;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Error mapping owned by the AI feature. Besides the usual ProblemDetail fields every AI error carries a stable
 * "code": AI_KEY_MISSING, AI_REJECTED, AI_TEMPORARILY_UNAVAILABLE (all 503) or AI_BAD_ANSWER (502).
 * Higher precedence than the shared catch-all in {@code common}.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AiExceptionHandler {

	@ExceptionHandler(AiUnavailableException.class)
	ProblemDetail handleUnavailable(AiUnavailableException ex) {
		return withCode(ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage()),
				ex.reason().code());
	}

	@ExceptionHandler(LlmResponseException.class)
	ProblemDetail handleBadAnswer(LlmResponseException ex) {
		return withCode(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
				"The LLM returned an answer that could not be used. Try again."), "AI_BAD_ANSWER");
	}

	/** The extra "code" field lets the web UI show its own (Italian) message without parsing English text. */
	private static ProblemDetail withCode(ProblemDetail problem, String code) {
		problem.setProperty("code", code);
		return problem;
	}
}
