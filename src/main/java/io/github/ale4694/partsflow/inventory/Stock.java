package io.github.ale4694.partsflow.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import java.math.BigDecimal;

/** Current quantity of one item. Created lazily by the first movement of that item. */
@Entity
public class Stock {

	@Id
	@Column(name = "item_id")
	private Long itemId;

	@Column(nullable = false)
	private BigDecimal quantity;

	/**
	 * Optimistic locking: Hibernate adds "where version = ?" to every update. If another transaction changed
	 * this row in the meantime, the update matches no row and fails instead of silently overwriting.
	 * A wrapper type (null until first save) lets Spring Data tell new rows from existing ones.
	 */
	@Version
	private Long version;

	protected Stock() {
		// required by JPA
	}

	public Stock(Long itemId) {
		this.itemId = itemId;
		this.quantity = BigDecimal.ZERO;
	}

	public Long getItemId() {
		return itemId;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public boolean canRemove(BigDecimal amount) {
		return quantity.compareTo(amount) >= 0;
	}

	public void add(BigDecimal amount) {
		quantity = quantity.add(amount);
	}

	public void remove(BigDecimal amount) {
		quantity = quantity.subtract(amount);
	}
}
