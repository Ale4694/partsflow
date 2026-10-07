package io.github.ale4694.partsflow.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

/** The web UI shows its own message per "code", so the codes are part of the API. */
class AiExceptionHandlerTest {

	private final AiExceptionHandler handler = new AiExceptionHandler();

	@Test
	void eachUnavailableReasonHasItsOwnCodeAndAnswers503() {
		assertCode(AiUnavailableException.Reason.KEY_MISSING, "AI_KEY_MISSING");
		assertCode(AiUnavailableException.Reason.REJECTED, "AI_REJECTED");
		assertCode(AiUnavailableException.Reason.TEMPORARILY_UNAVAILABLE, "AI_TEMPORARILY_UNAVAILABLE");
	}

	private void assertCode(AiUnavailableException.Reason reason, String code) {
		ProblemDetail problem = handler.handleUnavailable(new AiUnavailableException(reason, "some message"));

		assertThat(problem.getStatus()).isEqualTo(503);
		assertThat(problem.getDetail()).isEqualTo("some message");
		assertThat(problem.getProperties()).containsEntry("code", code);
	}

	@Test
	void anUnusableAnswerIsA502WithItsOwnCode() {
		ProblemDetail problem = handler.handleBadAnswer(new LlmResponseException("bad", new RuntimeException()));

		assertThat(problem.getStatus()).isEqualTo(502);
		assertThat(problem.getProperties()).containsEntry("code", "AI_BAD_ANSWER");
	}
}
