package io.github.ale4694.partsflow.inventory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.ale4694.partsflow.common.ApiExceptionHandler;
import io.github.ale4694.partsflow.common.PageResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InventoryController.class)
@Import(ApiExceptionHandler.class)
class InventoryControllerTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	InventoryService service;

	@Test
	void recordMovementReturns201() throws Exception {
		when(service.record(any())).thenReturn(new StockMovementResponse(1L, 10L, MovementType.IN,
				new BigDecimal("5"), "purchase", null, Instant.parse("2026-01-15T10:00:00Z")));

		mvc.perform(post("/api/inventory/movements").contentType(MediaType.APPLICATION_JSON).content("""
				{"itemId": 10, "type": "IN", "quantity": 5, "reason": "purchase"}"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/inventory/movements/1"))
				.andExpect(jsonPath("$.type").value("IN"));
	}

	@Test
	void insufficientStockReturns409() throws Exception {
		when(service.record(any())).thenThrow(new InsufficientStockException(10L, BigDecimal.ONE, BigDecimal.TEN));

		mvc.perform(post("/api/inventory/movements").contentType(MediaType.APPLICATION_JSON).content("""
				{"itemId": 10, "type": "OUT", "quantity": 10, "reason": "sale"}"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("Insufficient stock for item 10: available 1, requested 10"));
	}

	@Test
	void invalidMovementReturns400() throws Exception {
		mvc.perform(post("/api/inventory/movements").contentType(MediaType.APPLICATION_JSON).content("""
				{"itemId": 10, "type": "SIDEWAYS", "quantity": 0, "reason": ""}"""))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/inventory/movements").contentType(MediaType.APPLICATION_JSON).content("""
				{"itemId": 10, "type": "IN", "quantity": 0, "reason": "x"}"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void stockOfItem() throws Exception {
		when(service.getStock(10L)).thenReturn(
				new StockResponse(10L, "BRK-001", new BigDecimal("7.5"), "PZ", new BigDecimal("5")));

		mvc.perform(get("/api/inventory/stock/10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.quantity").value(7.5))
				.andExpect(jsonPath("$.itemCode").value("BRK-001"));
	}

	@Test
	void lowStockList() throws Exception {
		when(service.listLowStock(any())).thenReturn(new PageResponse<>(
				List.of(new LowStockItem(10L, "BRK-001", "Brake pads", "PZ", BigDecimal.ONE, new BigDecimal("5"))),
				0, 20, 1, 1));

		mvc.perform(get("/api/inventory/low-stock"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].itemCode").value("BRK-001"));
	}
}
