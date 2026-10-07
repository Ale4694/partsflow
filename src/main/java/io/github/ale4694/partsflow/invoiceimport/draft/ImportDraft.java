package io.github.ale4694.partsflow.invoiceimport.draft;

import io.github.ale4694.partsflow.catalog.Supplier;
import io.github.ale4694.partsflow.common.ConflictException;
import io.github.ale4694.partsflow.common.ResourceNotFoundException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The proposal built from an uploaded supplier document. Nothing touches the stock until {@link #confirm}.
 * The {@code version} column makes concurrent changes to the same draft fail instead of overwriting each other.
 */
@Entity
@Table(name = "import_draft")
public class ImportDraft {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "supplier_id")
	private Supplier supplier;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private DraftSource source;

	@Column(name = "tipo_documento", nullable = false)
	private String tipoDocumento;

	@Column(name = "document_number", nullable = false)
	private String documentNumber;

	@Column(name = "document_date", nullable = false)
	private LocalDate documentDate;

	@Column(name = "total_amount", nullable = false)
	private BigDecimal totalAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private DraftStatus status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "confirmed_at")
	private Instant confirmedAt;

	@Version
	private Long version;

	@ElementCollection
	@CollectionTable(name = "import_draft_ddt", joinColumns = @JoinColumn(name = "draft_id"))
	private List<DraftDdtReference> ddtReferences = new ArrayList<>();

	@OneToMany(mappedBy = "draft", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("lineNumber")
	private List<ImportDraftLine> lines = new ArrayList<>();

	protected ImportDraft() {
		// required by JPA
	}

	public ImportDraft(Supplier supplier, DraftSource source, String tipoDocumento, String documentNumber, LocalDate documentDate,
			BigDecimal totalAmount, Instant createdAt) {
		this.supplier = supplier;
		this.source = source;
		this.tipoDocumento = tipoDocumento;
		this.documentNumber = documentNumber;
		this.documentDate = documentDate;
		this.totalAmount = totalAmount;
		this.status = DraftStatus.DRAFT;
		this.createdAt = createdAt;
	}

	public void addLine(ImportDraftLine line) {
		line.attachTo(this);
		lines.add(line);
	}

	public void addDdtReference(DraftDdtReference reference) {
		ddtReferences.add(reference);
	}

	public ImportDraftLine getLine(Long lineId) {
		return lines.stream().filter(line -> line.getId().equals(lineId)).findFirst()
				.orElseThrow(() -> new ResourceNotFoundException("Draft line", lineId));
	}

	public long pendingLineCount() {
		return lines.stream().filter(line -> line.getStatus() == LineStatus.PENDING_REVIEW).count();
	}

	/** Lines can only be changed (or the draft deleted) while it is still a draft. */
	public void requireOpen() {
		if (status != DraftStatus.DRAFT) {
			throw new ConflictException("Draft " + id + " is already confirmed");
		}
	}

	/** A draft can only be confirmed when every line has been matched or skipped. */
	public void confirm(Instant now) {
		requireOpen();
		long pending = pendingLineCount();
		if (pending > 0) {
			throw new ConflictException(
					"Draft " + id + " still has " + pending + " line(s) pending review: resolve or skip them first");
		}
		this.status = DraftStatus.CONFIRMED;
		this.confirmedAt = now;
	}

	public Long getId() {
		return id;
	}

	public Supplier getSupplier() {
		return supplier;
	}

	public DraftSource getSource() {
		return source;
	}

	public String getTipoDocumento() {
		return tipoDocumento;
	}

	public String getDocumentNumber() {
		return documentNumber;
	}

	public LocalDate getDocumentDate() {
		return documentDate;
	}

	public BigDecimal getTotalAmount() {
		return totalAmount;
	}

	public DraftStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getConfirmedAt() {
		return confirmedAt;
	}

	public List<DraftDdtReference> getDdtReferences() {
		return ddtReferences;
	}

	public List<ImportDraftLine> getLines() {
		return lines;
	}
}
