CREATE TABLE mailboxes (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL,
    email_address VARCHAR(320) NOT NULL,
    imap_host     VARCHAR(255) NOT NULL,
    imap_port     INT NOT NULL,
    imap_ssl      BOOLEAN NOT NULL DEFAULT TRUE,
    password_enc  TEXT NOT NULL,
    folder        VARCHAR(255) NOT NULL DEFAULT 'INBOX',
    sync_enabled  BOOLEAN NOT NULL DEFAULT TRUE,
    uid_validity  BIGINT,
    last_seen_uid BIGINT,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    last_error    TEXT,
    last_sync_at  TIMESTAMP,
    created_at    TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'utc'),
    updated_at    TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'utc'),
    CONSTRAINT uq_mailboxes_user_email UNIQUE (user_id, email_address)
);

CREATE INDEX idx_mailboxes_user ON mailboxes (user_id);

CREATE INDEX idx_mailboxes_sync_enabled ON mailboxes (sync_enabled) WHERE sync_enabled = TRUE;
