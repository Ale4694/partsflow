package io.github.ale4694.partsflow.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StockRepository extends JpaRepository<Stock, Long> {

	/** Items with less stock than their reorder threshold. Items without a stock row count as quantity 0. */
	@Query(value = """
			select new io.github.ale4694.partsflow.inventory.LowStockItem(
			    i.id, i.code, i.description, i.unit, coalesce(s.quantity, 0), i.reorderThreshold)
			from Item i left join Stock s on s.itemId = i.id
			where coalesce(s.quantity, 0) < i.reorderThreshold
			order by i.code
			""",
			countQuery = """
					select count(i) from Item i left join Stock s on s.itemId = i.id
					where coalesce(s.quantity, 0) < i.reorderThreshold
					""")
	Page<LowStockItem> findLowStock(Pageable pageable);

	/** All items with their current stock. Items without a stock row count as quantity 0. */
	@Query(value = """
			select new io.github.ale4694.partsflow.inventory.StockLevel(
			    i.id, i.code, i.description, i.unit, coalesce(s.quantity, 0), i.reorderThreshold)
			from Item i left join Stock s on s.itemId = i.id
			order by i.code
			""",
			countQuery = "select count(i) from Item i")
	Page<StockLevel> findStockLevels(Pageable pageable);
}
