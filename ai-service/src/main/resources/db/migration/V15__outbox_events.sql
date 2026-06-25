CREATE TABLE outbox_events (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    topic        VARCHAR(255) NOT NULL,
    kafka_key    VARCHAR(255),
    payload      TEXT         NOT NULL,
    attempts     INT          NOT NULL DEFAULT 0,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    published_at TIMESTAMP
);

CREATE INDEX idx_outbox_events_unpublished
    ON outbox_events (created_at)
    WHERE published_at IS NULL;
