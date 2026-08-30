ALTER TABLE document_templates ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX idx_document_templates_deleted_at ON document_templates (deleted_at)
    WHERE deleted_at IS NOT NULL;
