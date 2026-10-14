CREATE TABLE orders (
    id              UUID PRIMARY KEY,
    user_id         UUID         NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    total_minor     BIGINT       NOT NULL CHECK (total_minor > 0),
    idempotency_key VARCHAR(100) NOT NULL,
    cancel_reason   VARCHAR(200),
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT orders_user_idem_uq UNIQUE (user_id, idempotency_key)
);

CREATE INDEX orders_user_created_idx ON orders (user_id, created_at DESC);
CREATE INDEX orders_status_updated_idx ON orders (status, updated_at);

CREATE TABLE order_items (
    id               UUID PRIMARY KEY,
    order_id         UUID         NOT NULL REFERENCES orders (id),
    product_id       UUID         NOT NULL,
    product_name     VARCHAR(120) NOT NULL,
    unit_price_minor BIGINT       NOT NULL,
    quantity         INT          NOT NULL CHECK (quantity > 0)
);

CREATE INDEX order_items_order_idx ON order_items (order_id);
