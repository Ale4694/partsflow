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
@RequestMapping("/api/suppliers/{supplierId}/item-codes")
public class SupplierItemCodeController {

	private final SupplierItemCodeService service;

	public SupplierItemCodeController(SupplierItemCodeService service) {
		this.service = service;
	}

	@PostMapping
	ResponseEntity<SupplierItemCodeResponse> create(@PathVariable Long supplierId,
			@Valid @RequestBody SupplierItemCodeRequest request) {
		SupplierItemCodeResponse created = service.create(supplierId, request);
		return ResponseEntity.created(URI.create("/api/suppliers/" + supplierId + "/item-codes/" + created.id()))
				.body(created);
	}

	@GetMapping
	PageResponse<SupplierItemCodeResponse> list(@PathVariable Long supplierId,
			@PageableDefault(size = 20, sort = "id") Pageable pageable) {
		return service.list(supplierId, pageable);
	}

	@GetMapping("/{id}")
	SupplierItemCodeResponse get(@PathVariable Long supplierId, @PathVariable Long id) {
		return service.get(supplierId, id);
	}

	@PutMapping("/{id}")
	SupplierItemCodeResponse update(@PathVariable Long supplierId, @PathVariable Long id,
			@Valid @RequestBody SupplierItemCodeRequest request) {
		return service.update(supplierId, id, request);
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable Long supplierId, @PathVariable Long id) {
		service.delete(supplierId, id);
		return ResponseEntity.noContent().build();
	}
}
