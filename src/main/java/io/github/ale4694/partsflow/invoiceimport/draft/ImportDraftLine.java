package io.github.ale4694.partsflow.invoiceimport.draft;

import io.github.ale4694.partsflow.catalog.Item;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "import_draft_line")
public class ImportDraftLine {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "draft_id")
	private ImportDraft draft;

	@Column(name = "line_number", nullable = false)
	private int lineNumber;

	/** The supplier's code for this line: the one that matched, otherwise the first one printed (if any). */
	@Column(name = "supplier_code")
	private String supplierCode;

	@Column(nullable = false)
	private String description;

	private BigDecimal quantity;

	private String unit;

	@Column(name = "unit_price")
	private BigDecimal unitPrice;

	@Column(name = "total_price", nullable = false)
	private BigDecimal totalPrice;

	@Column(name = "vat_rate", nullable = false)
	private BigDecimal vatRate;

	@Column(name = "stock_delta")
	private BigDecimal stockDelta;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private LineStatus status;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "item_id")
	private Item item;

	protected ImportDraftLine() {
		// required by JPA
	}

	public ImportDraftLine(int lineNumber, String supplierCode, String description, BigDecimal quantity, String unit,
			BigDecimal unitPrice, BigDecimal totalPrice, BigDecimal vatRate, BigDecimal stockDelta, LineStatus status,
			Item item) {
		this.lineNumber = lineNumber;
		this.supplierCode = supplierCode;
		this.description = description;
		this.quantity = quantity;
		this.unit = unit;
		this.unitPrice = unitPrice;
		this.totalPrice = totalPrice;
		this.vatRate = vatRate;
		this.stockDelta = stockDelta;
		this.status = status;
		this.item = item;
	}

	void attachTo(ImportDraft draft) {
		this.draft = draft;
	}

	public void resolve(Item item) {
		this.item = item;
		this.status = LineStatus.MATCHED;
	}

	public void skip() {
		this.status = LineStatus.SKIPPED;
	}

	public Long getId() {
		return id;
	}

	public int getLineNumber() {
		return lineNumber;
	}

	public String getSupplierCode() {
		return supplierCode;
	}

	public String getDescription() {
		return description;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public String getUnit() {
		return unit;
	}

	public BigDecimal getUnitPrice() {
		return unitPrice;
	}

	public BigDecimal getTotalPrice() {
		return totalPrice;
	}

	public BigDecimal getVatRate() {
		return vatRate;
	}

	public BigDecimal getStockDelta() {
		return stockDelta;
	}

	public LineStatus getStatus() {
		return status;
	}

	public Item getItem() {
		return item;
	}
}
