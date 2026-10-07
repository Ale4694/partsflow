package io.github.ale4694.partsflow.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.ale4694.partsflow.common.ApiExceptionHandler;
import io.github.ale4694.partsflow.common.ConflictException;
import io.github.ale4694.partsflow.common.PageResponse;
import io.github.ale4694.partsflow.common.ResourceNotFoundException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SupplierController.class)
@Import(ApiExceptionHandler.class)
class SupplierControllerTest {

	private static final String VALID_BODY = """
			{"name": "Ricambi Rossi Srl", "vatNumber": "01234567897"}""";

	@Autowired
	MockMvc mvc;

	@MockitoBean
	SupplierService service;

	@Test
	void createReturns201WithLocation() throws Exception {
		when(service.create(any())).thenReturn(new SupplierResponse(1L, "Ricambi Rossi Srl", "01234567897"));

		mvc.perform(post("/api/suppliers").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/suppliers/1"))
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.vatNumber").value("01234567897"));
	}

	@Test
	void createWithInvalidBodyReturns400ProblemDetail() throws Exception {
		mvc.perform(post("/api/suppliers").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "", "vatNumber": "abc"}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void createDuplicateReturns409() throws Exception {
		when(service.create(any())).thenThrow(new ConflictException("duplicate"));

		mvc.perform(post("/api/suppliers").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("duplicate"));
	}

	@Test
	void getUnknownReturns404() throws Exception {
		when(service.get(9L)).thenThrow(new ResourceNotFoundException("Supplier", 9L));

		mvc.perform(get("/api/suppliers/9"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("Fornitore 9 non trovato"));
	}

	@Test
	void listReturnsPageShape() throws Exception {
		when(service.list(any())).thenReturn(new PageResponse<>(
				List.of(new SupplierResponse(1L, "Ricambi Rossi Srl", "01234567897")), 0, 20, 1, 1));

		mvc.perform(get("/api/suppliers?page=0&size=20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].name").value("Ricambi Rossi Srl"))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.totalPages").value(1));
	}

	@Test
	void updateReturnsUpdatedSupplier() throws Exception {
		when(service.update(any(), any())).thenReturn(new SupplierResponse(1L, "Ricambi Rossi Srl", "01234567897"));

		mvc.perform(put("/api/suppliers/1").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Ricambi Rossi Srl"));
	}

	@Test
	void deleteReturns204() throws Exception {
		mvc.perform(delete("/api/suppliers/1")).andExpect(status().isNoContent());

		verify(service).delete(1L);
	}
}
