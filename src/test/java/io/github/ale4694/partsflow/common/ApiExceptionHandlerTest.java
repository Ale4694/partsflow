package io.github.ale4694.partsflow.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(ApiExceptionHandlerTest.BoomController.class)
// Nested test classes are skipped by component scanning, so the controller is imported explicitly
@Import({ApiExceptionHandler.class, ApiExceptionHandlerTest.BoomController.class})
class ApiExceptionHandlerTest {

	@RestController
	static class BoomController {
		@GetMapping("/boom")
		String boom() {
			throw new IllegalStateException("secret internal detail");
		}
	}

	@Autowired
	MockMvc mvc;

	@Test
	void unexpectedErrorIsAProblemDetailWithoutLeakingDetails() throws Exception {
		mvc.perform(get("/boom"))
				.andExpect(status().isInternalServerError())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(500))
				.andExpect(jsonPath("$.detail").value("Errore imprevisto del server"));
	}

	@Test
	void springMvcErrorsAreProblemDetailsToo() throws Exception {
		mvc.perform(post("/boom"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(405));
	}
}
