package io.github.ale4694.partsflow.catalog;

import io.github.ale4694.partsflow.common.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

	private final SupplierService service;

	public SupplierController(SupplierService service) {
		this.service = service;
	}

	@PostMapping
	ResponseEntity<SupplierResponse> create(@Valid @RequestBody SupplierRequest request) {
		SupplierResponse created = service.create(request);
		return ResponseEntity.created(URI.create("/api/suppliers/" + created.id())).body(created);
	}

	@GetMapping
	PageResponse<SupplierResponse> list(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
		return service.list(pageable);
	}

	@GetMapping("/{id}")
	SupplierResponse get(@PathVariable Long id) {
		return service.get(id);
	}

	@PutMapping("/{id}")
	SupplierResponse update(@PathVariable Long id, @Valid @RequestBody SupplierRequest request) {
		return service.update(id, request);
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable Long id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}
}
