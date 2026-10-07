package io.github.ale4694.partsflow.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

	Page<StockMovement> findByItemId(Long itemId, Pageable pageable);
}
