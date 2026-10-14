CREATE TABLE wallets (
    id            UUID PRIMARY KEY,
    user_id       UUID,
    type          VARCHAR(20) NOT NULL,
    code          VARCHAR(40) UNIQUE,
    currency      VARCHAR(3)  NOT NULL,
    balance_minor BIGINT      NOT NULL DEFAULT 0,
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version       BIGINT      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT wallets_user_currency_uq UNIQUE (user_id, currency),
    CONSTRAINT wallets_no_overdraft CHECK (type = 'SYSTEM' OR balance_minor >= 0)
);

CREATE TABLE ledger_transactions (
    id         UUID PRIMARY KEY,
    type       VARCHAR(20)  NOT NULL,
    reference  VARCHAR(140),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ledger_transactions_created_idx ON ledger_transactions (created_at DESC);

CREATE TABLE ledger_entries (
    id                  UUID PRIMARY KEY,
    seq                 BIGSERIAL   NOT NULL UNIQUE,
    transaction_id      UUID        NOT NULL REFERENCES ledger_transactions (id),
    wallet_id           UUID        NOT NULL REFERENCES wallets (id),
    direction           VARCHAR(6)  NOT NULL,
    amount_minor        BIGINT      NOT NULL CHECK (amount_minor > 0),
    balance_after_minor BIGINT      NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ledger_entries_wallet_created_idx ON ledger_entries (wallet_id, created_at);
CREATE INDEX ledger_entries_transaction_idx ON ledger_entries (transaction_id);

CREATE TABLE idempotency_keys (
    user_id         UUID         NOT NULL,
    idem_key        VARCHAR(100) NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,
    response_status INT          NOT NULL,
    response_body   TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, idem_key)
);

CREATE TABLE fraud_alerts (
    id             UUID PRIMARY KEY,
    transaction_id UUID         NOT NULL,
    wallet_path    TEXT         NOT NULL,
    detected_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
