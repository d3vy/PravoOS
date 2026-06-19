CREATE TABLE document_templates (
    id         UUID PRIMARY KEY,
    lawyer_id  UUID NOT NULL,
    name       VARCHAR(300) NOT NULL,
    content    TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_document_templates_lawyer ON document_templates (lawyer_id);
