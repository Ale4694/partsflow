-- Current quantity per item. "version" is the optimistic locking counter (JPA @Version).
CREATE TABLE stock (
    item_id  BIGINT         PRIMARY KEY REFERENCES item (id),
    quantity NUMERIC(14, 3) NOT NULL DEFAULT 0,
    version  BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_stock_quantity_not_negative CHECK (quantity >= 0)
);

-- Append-only history: current stock is the result of applying these movements.
CREATE TABLE stock_movement (
    id              BIGSERIAL PRIMARY KEY,
    item_id         BIGINT         NOT NULL REFERENCES item (id),
    type            VARCHAR(3)     NOT NULL,
    quantity        NUMERIC(14, 3) NOT NULL,
    reason          VARCHAR(200)   NOT NULL,
    source_document VARCHAR(200),
    created_at      TIMESTAMPTZ    NOT NULL,
    CONSTRAINT ck_stock_movement_type CHECK (type IN ('IN', 'OUT')),
    CONSTRAINT ck_stock_movement_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_stock_movement_item_created ON stock_movement (item_id, created_at DESC);
