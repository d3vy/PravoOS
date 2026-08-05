CREATE TABLE email_attachments (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email_message_id UUID NOT NULL REFERENCES email_messages (id) ON DELETE CASCADE,
    part_index       INT NOT NULL,
    file_name        VARCHAR(255) NOT NULL,
    content_type     VARCHAR(255),
    size_bytes       BIGINT NOT NULL DEFAULT 0,
    status           VARCHAR(20) NOT NULL,
    reason           VARCHAR(500),
    document_id      UUID,
    imported_at      TIMESTAMP,
    created_at       TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'utc'),
    CONSTRAINT uq_email_attachments_part UNIQUE (email_message_id, part_index)
);

CREATE INDEX idx_email_attachments_document ON email_attachments (document_id)
    WHERE document_id IS NOT NULL;
