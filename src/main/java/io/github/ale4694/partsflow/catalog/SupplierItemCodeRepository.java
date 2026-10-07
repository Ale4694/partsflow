package io.github.ale4694.partsflow.catalog;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierItemCodeRepository extends JpaRepository<SupplierItemCode, Long> {

	Page<SupplierItemCode> findBySupplierId(Long supplierId, Pageable pageable);

	Optional<SupplierItemCode> findByIdAndSupplierId(Long id, Long supplierId);

	/** Used by the invoice import to resolve a supplier's code to our item. */
	Optional<SupplierItemCode> findBySupplierIdAndSupplierCode(Long supplierId, String supplierCode);

	boolean existsBySupplierIdAndSupplierCode(Long supplierId, String supplierCode);

	boolean existsBySupplierIdAndSupplierCodeAndIdNot(Long supplierId, String supplierCode, Long id);
}
