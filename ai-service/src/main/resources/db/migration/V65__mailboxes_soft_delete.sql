ALTER TABLE mailboxes ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX idx_mailboxes_user_active ON mailboxes (user_id, created_at)
    WHERE deleted_at IS NULL;
