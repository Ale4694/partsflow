package io.github.ale4694.partsflow.catalog;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, Long> {

	/** Case-insensitive search in code and description, used by the item pickers of a UI. */
	Page<Item> findByCodeContainingIgnoreCaseOrDescriptionContainingIgnoreCase(String code, String description,
			Pageable pageable);

	Optional<Item> findByCode(String code);

	boolean existsByCode(String code);

	boolean existsByCodeAndIdNot(String code, Long id);
}
