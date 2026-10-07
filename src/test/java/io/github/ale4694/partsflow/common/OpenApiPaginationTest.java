package io.github.ale4694.partsflow.common;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.ale4694.partsflow.TestcontainersConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** Swagger UI renders Pageable as three query fields only if the OpenAPI document lists them separately. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiPaginationTest {

	@Autowired
	MockMvc mvc;

	@ParameterizedTest
	@ValueSource(strings = {
			"/api/suppliers",
			"/api/items",
			"/api/suppliers/{supplierId}/item-codes",
			"/api/inventory/movements",
			"/api/inventory/low-stock",
			"/api/imports"})
	void pageableIsDocumentedAsPageSizeAndSortQueryParameters(String path) throws Exception {
		String parameterNames = "$.paths['" + path + "'].get.parameters[?(@.in == 'query' && "
				+ "(@.name == 'page' || @.name == 'size' || @.name == 'sort'))].name";
		mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath(parameterNames, containsInAnyOrder("page", "size", "sort")))
				// no single JSON-object parameter called "pageable"
				.andExpect(jsonPath("$.paths['" + path + "'].get.parameters[?(@.name == 'pageable')]").isEmpty());
	}
}
