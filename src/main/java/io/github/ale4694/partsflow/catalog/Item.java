package io.github.ale4694.partsflow.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;

@Entity
public class Item {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Our own internal code, unique across the catalog. */
	@Column(nullable = false)
	private String code;

	@Column(nullable = false)
	private String description;

	/** Unit of measure, e.g. PZ (pieces), KG, LT. */
	@Column(nullable = false)
	private String unit;

	/** Stock at or below this quantity means the item should be reordered. */
	@Column(name = "reorder_threshold", nullable = false)
	private BigDecimal reorderThreshold;

	protected Item() {
		// required by JPA
	}

	public Item(String code, String description, String unit, BigDecimal reorderThreshold) {
		this.code = code;
		this.description = description;
		this.unit = unit;
		this.reorderThreshold = reorderThreshold;
	}

	public Long getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getDescription() {
		return description;
	}

	public String getUnit() {
		return unit;
	}

	public BigDecimal getReorderThreshold() {
		return reorderThreshold;
	}

	public void update(String code, String description, String unit, BigDecimal reorderThreshold) {
		this.code = code;
		this.description = description;
		this.unit = unit;
		this.reorderThreshold = reorderThreshold;
	}
}
