package io.github.ale4694.partsflow.common;

import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.ale4694.partsflow.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Every paginated endpoint must answer an unknown sort property with 400 and a ProblemDetail naming it,
 * whichever repository query sits behind the endpoint (Swagger UI sends sort=string by default).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InvalidSortIntegrationTest {

	@Autowired
	MockMvc mvc;

	@Test
	void unknownSortPropertyIsABadRequestOnEveryPaginatedEndpoint() throws Exception {
		long supplierId = createAndReturnId("/api/suppliers", """
				{"name": "Sort Test Srl", "vatNumber": "10000000099"}""");
		long itemId = createAndReturnId("/api/items", """
				{"code": "SORT-001", "description": "Sort test item", "unit": "PZ", "reorderThreshold": 1}""");

		List<String> urls = List.of(
				"/api/suppliers",
				"/api/items",
				"/api/suppliers/" + supplierId + "/item-codes",
				"/api/inventory/movements",
				"/api/inventory/movements?itemId=" + itemId,
				"/api/imports",
				"/api/imports?status=DRAFT");

		for (String url : urls) {
			// Swagger UI sends the literal text ["string"] by default; other clients send "property,direction"
			for (String sort : List.of("string", "string,desc", "[\"string\"]")) {
				mvc.perform(get(url).param("page", "0").param("size", "1").param("sort", sort))
						.andExpect(status().isBadRequest())
						.andExpect(jsonPath("$.status").value(400))
						.andExpect(jsonPath("$.detail").value(containsString("Unknown sort property: ")))
						.andExpect(jsonPath("$.detail").value(containsString("string")));
			}
		}
	}

	@Test
	void validSortStillWorks() throws Exception {
		mvc.perform(get("/api/items?sort=code,desc")).andExpect(status().isOk());
		mvc.perform(get("/api/inventory/movements?sort=createdAt,asc")).andExpect(status().isOk());
	}

	@Test
	void lowStockIgnoresSortByDesign() throws Exception {
		assertThat(mvc.perform(get("/api/inventory/low-stock?sort=string")).andReturn().getResponse().getStatus())
				.isEqualTo(200);
	}

	private long createAndReturnId(String url, String json) throws Exception {
		MvcResult result = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isCreated()).andReturn();
		String location = result.getResponse().getHeader("Location");
		return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
	}
}
