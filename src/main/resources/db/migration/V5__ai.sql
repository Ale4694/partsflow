-- Where a draft came from: deterministic XML parsing or LLM extraction from a PDF
ALTER TABLE import_draft ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'XML';
ALTER TABLE import_draft ADD CONSTRAINT ck_import_draft_source CHECK (source IN ('XML', 'PDF'));

-- LLM proposal for a pending draft line. It never changes anything by itself: a person accepts or rejects it.
CREATE TABLE line_match_suggestion (
    id            BIGSERIAL PRIMARY KEY,
    draft_id      BIGINT        NOT NULL REFERENCES import_draft (id) ON DELETE CASCADE,
    draft_line_id BIGINT        NOT NULL REFERENCES import_draft_line (id) ON DELETE CASCADE,
    -- NULL means "no candidate is the same product"
    item_id       BIGINT REFERENCES item (id) ON DELETE SET NULL,
    justification VARCHAR(1000) NOT NULL,
    status        VARCHAR(10)   NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_line_match_suggestion_line UNIQUE (draft_line_id),
    CONSTRAINT ck_line_match_suggestion_status CHECK (status IN ('SUGGESTED', 'ACCEPTED', 'REJECTED'))
);

CREATE INDEX idx_line_match_suggestion_draft ON line_match_suggestion (draft_id);
