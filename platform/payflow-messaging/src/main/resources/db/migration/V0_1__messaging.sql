CREATE TABLE outbox_messages (
    id           UUID PRIMARY KEY,
    topic        VARCHAR(120) NOT NULL,
    message_key  VARCHAR(120) NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    attempts     INT          NOT NULL DEFAULT 0,
    last_error   VARCHAR(500)
);

CREATE INDEX outbox_unpublished_idx ON outbox_messages (created_at) WHERE published_at IS NULL;

CREATE TABLE processed_messages (
    consumer     VARCHAR(80)  NOT NULL,
    message_id   VARCHAR(120) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, message_id)
);
