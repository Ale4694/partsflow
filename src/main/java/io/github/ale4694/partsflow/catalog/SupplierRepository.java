package io.github.ale4694.partsflow.catalog;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

	Optional<Supplier> findByVatNumber(String vatNumber);

	boolean existsByVatNumber(String vatNumber);

	boolean existsByVatNumberAndIdNot(String vatNumber, Long id);
}
