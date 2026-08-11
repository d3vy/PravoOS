ALTER TABLE mailboxes
    ADD COLUMN version                 BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN consecutive_failures    INT    NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at         TIMESTAMP,
    ADD COLUMN unreadable_uid          BIGINT,
    ADD COLUMN unreadable_uid_attempts INT    NOT NULL DEFAULT 0;

DROP INDEX idx_mailboxes_sync_enabled;

CREATE INDEX idx_mailboxes_due_for_sync ON mailboxes (next_attempt_at)
    WHERE sync_enabled = TRUE;

ALTER TABLE email_messages
    ALTER COLUMN subject TYPE TEXT,
    ALTER COLUMN from_address TYPE TEXT,
    ADD COLUMN auto_link_attempts INT NOT NULL DEFAULT 0;

DROP INDEX idx_email_messages_unlinked;

CREATE INDEX idx_email_messages_unlinked ON email_messages (mailbox_id, auto_link_attempts, sent_at DESC)
    WHERE case_id IS NULL AND client_id IS NULL;
