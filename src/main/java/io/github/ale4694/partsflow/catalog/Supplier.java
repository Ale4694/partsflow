package io.github.ale4694.partsflow.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Supplier {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(name = "vat_number", nullable = false)
	private String vatNumber;

	protected Supplier() {
		// required by JPA
	}

	public Supplier(String name, String vatNumber) {
		this.name = name;
		this.vatNumber = vatNumber;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getVatNumber() {
		return vatNumber;
	}

	public void update(String name, String vatNumber) {
		this.name = name;
		this.vatNumber = vatNumber;
	}
}
