CREATE TABLE email_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mailbox_id      UUID NOT NULL REFERENCES mailboxes (id) ON DELETE CASCADE,
    message_id      VARCHAR(500) NOT NULL,
    imap_uid        BIGINT NOT NULL,
    thread_key      VARCHAR(500),
    direction       VARCHAR(10) NOT NULL,
    from_address    VARCHAR(320),
    to_addresses    TEXT,
    cc_addresses    TEXT,
    subject         VARCHAR(1000),
    body_text       TEXT,
    sent_at         TIMESTAMP,
    has_attachments BOOLEAN NOT NULL DEFAULT FALSE,
    attachment_count INT NOT NULL DEFAULT 0,
    case_id         UUID,
    client_id       UUID,
    created_at      TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'utc'),
    CONSTRAINT uq_email_messages_mailbox_message UNIQUE (mailbox_id, message_id)
);

CREATE INDEX idx_email_messages_mailbox_uid ON email_messages (mailbox_id, imap_uid DESC);

CREATE INDEX idx_email_messages_case ON email_messages (case_id, sent_at DESC)
    WHERE case_id IS NOT NULL;

CREATE INDEX idx_email_messages_client ON email_messages (client_id, sent_at DESC)
    WHERE client_id IS NOT NULL;

CREATE INDEX idx_email_messages_thread ON email_messages (mailbox_id, thread_key)
    WHERE thread_key IS NOT NULL;

CREATE INDEX idx_email_messages_unlinked ON email_messages (mailbox_id, sent_at DESC)
    WHERE case_id IS NULL AND client_id IS NULL;
