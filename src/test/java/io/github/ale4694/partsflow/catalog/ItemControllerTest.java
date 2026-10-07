package io.github.ale4694.partsflow.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.ale4694.partsflow.common.ApiExceptionHandler;
import io.github.ale4694.partsflow.common.ResourceNotFoundException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ItemController.class)
@Import(ApiExceptionHandler.class)
class ItemControllerTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	ItemService service;

	@Test
	void createReturns201() throws Exception {
		when(service.create(any())).thenReturn(
				new ItemResponse(1L, "BRK-001", "Front brake pad set", "PZ", new BigDecimal("5")));

		mvc.perform(post("/api/items").contentType(MediaType.APPLICATION_JSON).content("""
				{"code": "BRK-001", "description": "Front brake pad set", "unit": "PZ", "reorderThreshold": 5}"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/items/1"))
				.andExpect(jsonPath("$.code").value("BRK-001"));
	}

	@Test
	void createRejectsNegativeThresholdAndMissingFields() throws Exception {
		mvc.perform(post("/api/items").contentType(MediaType.APPLICATION_JSON).content("""
				{"code": "", "unit": "PZ", "reorderThreshold": -1}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void getUnknownReturns404() throws Exception {
		when(service.get(3L)).thenThrow(new ResourceNotFoundException("Item", 3L));

		mvc.perform(get("/api/items/3")).andExpect(status().isNotFound());
	}
}
