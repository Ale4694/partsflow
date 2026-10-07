package io.github.ale4694.partsflow.ai;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Error mapping owned by the AI feature; higher precedence than the shared catch-all in {@code common}. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AiExceptionHandler {

	@ExceptionHandler(AiUnavailableException.class)
	ProblemDetail handleUnavailable(AiUnavailableException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
	}

	@ExceptionHandler(LlmResponseException.class)
	ProblemDetail handleBadAnswer(LlmResponseException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
				"The LLM returned an answer that could not be used. Try again.");
	}
}
