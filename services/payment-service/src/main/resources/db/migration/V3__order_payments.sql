CREATE TABLE order_payments (
    order_id              UUID PRIMARY KEY,
    user_id               UUID        NOT NULL,
    amount_minor          BIGINT      NOT NULL CHECK (amount_minor > 0),
    transaction_id        UUID        NOT NULL,
    status                VARCHAR(20) NOT NULL,
    refund_transaction_id UUID,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
