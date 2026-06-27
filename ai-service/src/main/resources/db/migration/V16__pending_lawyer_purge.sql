CREATE TABLE pending_lawyer_purge (
    lawyer_id       UUID      PRIMARY KEY,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    last_attempt_at TIMESTAMP,
    attempts        INT       NOT NULL DEFAULT 0,
    last_error      TEXT
);
