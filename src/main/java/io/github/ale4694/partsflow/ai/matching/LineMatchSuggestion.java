package io.github.ale4694.partsflow.ai.matching;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** The LLM's proposal for one pending draft line. It changes nothing until a person accepts it. */
@Entity
@Table(name = "line_match_suggestion")
public class LineMatchSuggestion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "draft_id", nullable = false)
	private Long draftId;

	@Column(name = "draft_line_id", nullable = false)
	private Long draftLineId;

	/** null = the LLM found no candidate that is clearly the same product. */
	@Column(name = "item_id")
	private Long itemId;

	@Column(nullable = false)
	private String justification;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SuggestionStatus status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected LineMatchSuggestion() {
		// required by JPA
	}

	public LineMatchSuggestion(Long draftId, Long draftLineId, Long itemId, String justification, Instant createdAt) {
		this.draftId = draftId;
		this.draftLineId = draftLineId;
		this.status = SuggestionStatus.SUGGESTED;
		replaceWith(itemId, justification, createdAt);
	}

	/** A rejected suggestion can be replaced by a new attempt. */
	public void replaceWith(Long itemId, String justification, Instant createdAt) {
		this.itemId = itemId;
		this.justification = justification;
		this.createdAt = createdAt;
		this.status = SuggestionStatus.SUGGESTED;
	}

	public void markAccepted() {
		this.status = SuggestionStatus.ACCEPTED;
	}

	public void markRejected() {
		this.status = SuggestionStatus.REJECTED;
	}

	public Long getId() {
		return id;
	}

	public Long getDraftId() {
		return draftId;
	}

	public Long getDraftLineId() {
		return draftLineId;
	}

	public Long getItemId() {
		return itemId;
	}

	public String getJustification() {
		return justification;
	}

	public SuggestionStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
