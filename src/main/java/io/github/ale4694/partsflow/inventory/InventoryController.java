package io.github.ale4694.partsflow.inventory;

import io.github.ale4694.partsflow.common.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

	private final InventoryService service;

	public InventoryController(InventoryService service) {
		this.service = service;
	}

	@PostMapping("/movements")
	ResponseEntity<StockMovementResponse> record(@Valid @RequestBody StockMovementRequest request) {
		StockMovementResponse created = service.record(request);
		return ResponseEntity.created(URI.create("/api/inventory/movements/" + created.id())).body(created);
	}

	/** Movement history, newest first; optionally for a single item. */
	@GetMapping("/movements")
	PageResponse<StockMovementResponse> movements(@RequestParam(required = false) Long itemId,
			@ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return service.listMovements(itemId, pageable);
	}

	@GetMapping("/stock/{itemId}")
	StockResponse stock(@PathVariable Long itemId) {
		return service.getStock(itemId);
	}

	/** Every item with its current stock, ordered by item code (a client-supplied sort is ignored). */
	@GetMapping("/stock")
	PageResponse<StockLevel> stockLevels(@ParameterObject @PageableDefault(size = 20) Pageable pageable) {
		return service.listStock(pageable);
	}

	@GetMapping("/low-stock")
	PageResponse<LowStockItem> lowStock(@ParameterObject @PageableDefault(size = 20) Pageable pageable) {
		return service.listLowStock(pageable);
	}
}
