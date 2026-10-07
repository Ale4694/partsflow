package io.github.ale4694.partsflow.catalog;

import io.github.ale4694.partsflow.common.ConflictException;
import io.github.ale4694.partsflow.common.PageResponse;
import io.github.ale4694.partsflow.common.ResourceNotFoundException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SupplierItemCodeService {

	private final SupplierItemCodeRepository mappings;
	private final SupplierService supplierService;
	private final ItemService itemService;

	public SupplierItemCodeService(SupplierItemCodeRepository mappings, SupplierService supplierService,
			ItemService itemService) {
		this.mappings = mappings;
		this.supplierService = supplierService;
		this.itemService = itemService;
	}

	public SupplierItemCodeResponse create(Long supplierId, SupplierItemCodeRequest request) {
		Supplier supplier = supplierService.find(supplierId);
		Item item = itemService.find(request.itemId());
		if (mappings.existsBySupplierIdAndSupplierCode(supplierId, request.supplierCode())) {
			throw duplicate(request.supplierCode());
		}
		return SupplierItemCodeResponse.from(mappings.save(new SupplierItemCode(supplier, item, request.supplierCode())));
	}

	@Transactional(readOnly = true)
	public SupplierItemCodeResponse get(Long supplierId, Long id) {
		return SupplierItemCodeResponse.from(find(supplierId, id));
	}

	@Transactional(readOnly = true)
	public PageResponse<SupplierItemCodeResponse> list(Long supplierId, Pageable pageable) {
		supplierService.find(supplierId); // 404 if the supplier does not exist
		return PageResponse.from(mappings.findBySupplierId(supplierId, pageable).map(SupplierItemCodeResponse::from));
	}

	public SupplierItemCodeResponse update(Long supplierId, Long id, SupplierItemCodeRequest request) {
		SupplierItemCode mapping = find(supplierId, id);
		Item item = itemService.find(request.itemId());
		if (mappings.existsBySupplierIdAndSupplierCodeAndIdNot(supplierId, request.supplierCode(), id)) {
			throw duplicate(request.supplierCode());
		}
		mapping.update(item, request.supplierCode());
		return SupplierItemCodeResponse.from(mapping);
	}

	public void delete(Long supplierId, Long id) {
		mappings.delete(find(supplierId, id));
	}

	private SupplierItemCode find(Long supplierId, Long id) {
		return mappings.findByIdAndSupplierId(id, supplierId)
				.orElseThrow(() -> new ResourceNotFoundException("Supplier item code", id));
	}

	private ConflictException duplicate(String supplierCode) {
		return new ConflictException("This supplier already has a mapping for code " + supplierCode);
	}
}
