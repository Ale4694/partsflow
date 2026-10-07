-- A draft is the proposal built from an uploaded document. Stock only changes when a draft is confirmed.
CREATE TABLE import_draft (
    id              BIGSERIAL PRIMARY KEY,
    supplier_id     BIGINT         NOT NULL REFERENCES supplier (id),
    tipo_documento  VARCHAR(4)     NOT NULL,
    document_number VARCHAR(50)    NOT NULL,
    document_date   DATE           NOT NULL,
    total_amount    NUMERIC(14, 2) NOT NULL,
    status          VARCHAR(10)    NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL,
    confirmed_at    TIMESTAMPTZ,
    version         BIGINT         NOT NULL DEFAULT 0,
    -- Idempotency: the same document from the same supplier can only be imported once
    CONSTRAINT uq_import_draft_document UNIQUE (supplier_id, document_number, document_date),
    CONSTRAINT ck_import_draft_status CHECK (status IN ('DRAFT', 'CONFIRMED'))
);

CREATE TABLE import_draft_ddt (
    draft_id   BIGINT      NOT NULL REFERENCES import_draft (id) ON DELETE CASCADE,
    ddt_number VARCHAR(50) NOT NULL,
    ddt_date   DATE
);

CREATE INDEX idx_import_draft_ddt_draft ON import_draft_ddt (draft_id);

CREATE TABLE import_draft_line (
    id            BIGSERIAL PRIMARY KEY,
    draft_id      BIGINT         NOT NULL REFERENCES import_draft (id) ON DELETE CASCADE,
    line_number   INT            NOT NULL,
    supplier_code VARCHAR(100),
    description   VARCHAR(1000)  NOT NULL,
    quantity      NUMERIC(14, 3),
    unit          VARCHAR(10),
    unit_price    NUMERIC(18, 8),
    total_price   NUMERIC(18, 8) NOT NULL,
    vat_rate      NUMERIC(5, 2)  NOT NULL,
    -- How much confirming changes the stock: positive adds, negative removes (credit notes reverse quantities)
    stock_delta   NUMERIC(14, 3),
    status        VARCHAR(15)    NOT NULL,
    item_id       BIGINT REFERENCES item (id),
    CONSTRAINT ck_import_draft_line_status CHECK (status IN ('MATCHED', 'PENDING_REVIEW', 'SKIPPED')),
    CONSTRAINT ck_import_draft_line_matched_has_item CHECK (status <> 'MATCHED' OR item_id IS NOT NULL)
);

CREATE INDEX idx_import_draft_line_draft ON import_draft_line (draft_id);
CREATE INDEX idx_import_draft_line_item ON import_draft_line (item_id);
