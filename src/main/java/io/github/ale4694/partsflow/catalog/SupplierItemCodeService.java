package io.github.ale4694.partsflow.catalog;

import io.github.ale4694.partsflow.common.ConflictException;
import io.github.ale4694.partsflow.common.PageResponse;
import io.github.ale4694.partsflow.common.ResourceNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SupplierItemCodeService {

	private final SupplierItemCodeRepository mappings;
	private final SupplierService supplierService;
	private final ItemService itemService;
	private final ApplicationEventPublisher events;

	public SupplierItemCodeService(SupplierItemCodeRepository mappings, SupplierService supplierService,
			ItemService itemService, ApplicationEventPublisher events) {
		this.mappings = mappings;
		this.supplierService = supplierService;
		this.itemService = itemService;
		this.events = events;
	}

	public SupplierItemCodeResponse create(Long supplierId, SupplierItemCodeRequest request) {
		Supplier supplier = supplierService.find(supplierId);
		Item item = itemService.find(request.itemId());
		if (mappings.existsBySupplierIdAndSupplierCode(supplierId, request.supplierCode())) {
			throw duplicate(request.supplierCode());
		}
		SupplierItemCode saved = mappings.save(new SupplierItemCode(supplier, item, request.supplierCode()));
		// the supplier codes are part of the text the semantic search reads about an item
		events.publishEvent(ItemChanged.of(item.getId()));
		return SupplierItemCodeResponse.from(saved);
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
		Long previousItemId = mapping.getItem().getId();
		mapping.update(item, request.supplierCode());
		events.publishEvent(ItemChanged.of(previousItemId, item.getId()));
		return SupplierItemCodeResponse.from(mapping);
	}

	public void delete(Long supplierId, Long id) {
		SupplierItemCode mapping = find(supplierId, id);
		mappings.delete(mapping);
		events.publishEvent(ItemChanged.of(mapping.getItem().getId()));
	}

	private SupplierItemCode find(Long supplierId, Long id) {
		return mappings.findByIdAndSupplierId(id, supplierId)
				.orElseThrow(() -> new ResourceNotFoundException("Supplier item code", id));
	}

	private ConflictException duplicate(String supplierCode) {
		return new ConflictException("Questo fornitore ha già un abbinamento per il codice " + supplierCode);
	}
}
