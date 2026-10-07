package io.github.ale4694.partsflow.invoiceimport.draft;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDate;

@Embeddable
public class DraftDdtReference {

	@Column(name = "ddt_number", nullable = false)
	private String number;

	@Column(name = "ddt_date")
	private LocalDate date;

	protected DraftDdtReference() {
		// required by JPA
	}

	public DraftDdtReference(String number, LocalDate date) {
		this.number = number;
		this.date = date;
	}

	public String getNumber() {
		return number;
	}

	public LocalDate getDate() {
		return date;
	}
}
