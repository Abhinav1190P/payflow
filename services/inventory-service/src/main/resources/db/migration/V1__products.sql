CREATE TABLE products (
    id          UUID PRIMARY KEY,
    sku         VARCHAR(40)  NOT NULL UNIQUE,
    name        VARCHAR(120) NOT NULL,
    price_minor BIGINT       NOT NULL CHECK (price_minor >= 0),
    stock       INT          NOT NULL DEFAULT 0 CHECK (stock >= 0),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE stock_reservations (
    order_id   UUID        NOT NULL,
    product_id UUID        NOT NULL REFERENCES products (id),
    quantity   INT         NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (order_id, product_id)
);
