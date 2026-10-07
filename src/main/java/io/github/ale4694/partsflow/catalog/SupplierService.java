package io.github.ale4694.partsflow.catalog;

import io.github.ale4694.partsflow.common.ConflictException;
import io.github.ale4694.partsflow.common.PageResponse;
import io.github.ale4694.partsflow.common.ResourceNotFoundException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SupplierService {

	private final SupplierRepository suppliers;

	public SupplierService(SupplierRepository suppliers) {
		this.suppliers = suppliers;
	}

	public SupplierResponse create(SupplierRequest request) {
		if (suppliers.existsByVatNumber(request.vatNumber())) {
			throw new ConflictException("Esiste già un fornitore con partita IVA " + request.vatNumber());
		}
		return SupplierResponse.from(suppliers.save(new Supplier(request.name(), request.vatNumber())));
	}

	@Transactional(readOnly = true)
	public SupplierResponse get(Long id) {
		return SupplierResponse.from(find(id));
	}

	@Transactional(readOnly = true)
	public PageResponse<SupplierResponse> list(Pageable pageable) {
		return PageResponse.from(suppliers.findAll(pageable).map(SupplierResponse::from));
	}

	public SupplierResponse update(Long id, SupplierRequest request) {
		Supplier supplier = find(id);
		if (suppliers.existsByVatNumberAndIdNot(request.vatNumber(), id)) {
			throw new ConflictException("Esiste già un fornitore con partita IVA " + request.vatNumber());
		}
		supplier.update(request.name(), request.vatNumber());
		return SupplierResponse.from(supplier);
	}

	public void delete(Long id) {
		suppliers.delete(find(id));
		// Flush now so a foreign key violation (supplier still has item codes) surfaces as a 409 here
		suppliers.flush();
	}

	/** Shared with the other catalog services that need the supplier entity. */
	Supplier find(Long id) {
		return suppliers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Supplier", id));
	}
}
