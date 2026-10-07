package io.github.ale4694.partsflow.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "stock_movement")
public class StockMovement {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "item_id", nullable = false)
	private Long itemId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MovementType type;

	/** Always positive; the type says whether it is added or removed. */
	@Column(nullable = false)
	private BigDecimal quantity;

	@Column(nullable = false)
	private String reason;

	/** Reference to what caused the movement, e.g. "invoice 123/2025 from supplier X". Optional. */
	@Column(name = "source_document")
	private String sourceDocument;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected StockMovement() {
		// required by JPA
	}

	public StockMovement(Long itemId, MovementType type, BigDecimal quantity, String reason, String sourceDocument,
			Instant createdAt) {
		this.itemId = itemId;
		this.type = type;
		this.quantity = quantity;
		this.reason = reason;
		this.sourceDocument = sourceDocument;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public Long getItemId() {
		return itemId;
	}

	public MovementType getType() {
		return type;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public String getReason() {
		return reason;
	}

	public String getSourceDocument() {
		return sourceDocument;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
