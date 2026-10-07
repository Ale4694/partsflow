package io.github.ale4694.partsflow.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

	boolean existsByVatNumber(String vatNumber);

	boolean existsByVatNumberAndIdNot(String vatNumber, Long id);
}
