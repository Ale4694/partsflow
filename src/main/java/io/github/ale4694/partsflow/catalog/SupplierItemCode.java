package io.github.ale4694.partsflow.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Maps the code a supplier uses on its invoices to one of our internal items. */
@Entity
@Table(name = "supplier_item_code")
public class SupplierItemCode {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "supplier_id")
	private Supplier supplier;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "item_id")
	private Item item;

	@Column(name = "supplier_code", nullable = false)
	private String supplierCode;

	protected SupplierItemCode() {
		// required by JPA
	}

	public SupplierItemCode(Supplier supplier, Item item, String supplierCode) {
		this.supplier = supplier;
		this.item = item;
		this.supplierCode = supplierCode;
	}

	public Long getId() {
		return id;
	}

	public Supplier getSupplier() {
		return supplier;
	}

	public Item getItem() {
		return item;
	}

	public String getSupplierCode() {
		return supplierCode;
	}

	public void update(Item item, String supplierCode) {
		this.item = item;
		this.supplierCode = supplierCode;
	}
}
