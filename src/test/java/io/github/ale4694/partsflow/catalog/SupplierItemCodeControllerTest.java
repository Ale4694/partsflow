package io.github.ale4694.partsflow.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.ale4694.partsflow.common.ApiExceptionHandler;
import io.github.ale4694.partsflow.common.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SupplierItemCodeController.class)
@Import(ApiExceptionHandler.class)
class SupplierItemCodeControllerTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	SupplierItemCodeService service;

	@Test
	void createReturns201() throws Exception {
		when(service.create(any(), any())).thenReturn(new SupplierItemCodeResponse(5L, 1L, "RR-4411", 10L, "BRK-001"));

		mvc.perform(post("/api/suppliers/1/item-codes").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"supplierCode": "RR-4411", "itemId": 10}"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/suppliers/1/item-codes/5"))
				.andExpect(jsonPath("$.itemCode").value("BRK-001"));
	}

	@Test
	void createWithoutItemIdReturns400() throws Exception {
		mvc.perform(post("/api/suppliers/1/item-codes").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"supplierCode": "RR-4411"}"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createForUnknownSupplierReturns404() throws Exception {
		when(service.create(any(), any())).thenThrow(new ResourceNotFoundException("Supplier", 99L));

		mvc.perform(post("/api/suppliers/99/item-codes").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"supplierCode": "RR-4411", "itemId": 10}"""))
				.andExpect(status().isNotFound());
	}

	@Test
	void deleteReturns204() throws Exception {
		mvc.perform(delete("/api/suppliers/1/item-codes/5")).andExpect(status().isNoContent());

		verify(service).delete(1L, 5L);
	}
}
