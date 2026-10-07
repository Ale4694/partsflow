CREATE TABLE supplier (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    vat_number VARCHAR(28)  NOT NULL,
    CONSTRAINT uq_supplier_vat_number UNIQUE (vat_number)
);

CREATE TABLE item (
    id                BIGSERIAL PRIMARY KEY,
    code              VARCHAR(50)    NOT NULL,
    description       VARCHAR(500)   NOT NULL,
    unit              VARCHAR(10)    NOT NULL,
    reorder_threshold NUMERIC(14, 3) NOT NULL DEFAULT 0,
    CONSTRAINT uq_item_code UNIQUE (code),
    CONSTRAINT ck_item_reorder_threshold CHECK (reorder_threshold >= 0)
);

-- The code a supplier uses for one of our items (FatturaPA CodiceArticolo)
CREATE TABLE supplier_item_code (
    id            BIGSERIAL PRIMARY KEY,
    supplier_id   BIGINT       NOT NULL REFERENCES supplier (id),
    item_id       BIGINT       NOT NULL REFERENCES item (id),
    supplier_code VARCHAR(100) NOT NULL,
    CONSTRAINT uq_supplier_item_code UNIQUE (supplier_id, supplier_code)
);

CREATE INDEX idx_supplier_item_code_item ON supplier_item_code (item_id);
