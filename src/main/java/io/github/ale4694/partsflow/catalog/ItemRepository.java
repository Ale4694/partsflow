package io.github.ale4694.partsflow.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, Long> {

	boolean existsByCode(String code);

	boolean existsByCodeAndIdNot(String code, Long id);
}
