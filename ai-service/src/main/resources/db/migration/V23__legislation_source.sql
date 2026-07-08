ALTER TABLE documents ADD COLUMN document_kind VARCHAR(50) NOT NULL DEFAULT 'GENERAL';
ALTER TABLE documents ADD COLUMN act_canonical VARCHAR(300);
ALTER TABLE documents ADD COLUMN article_number VARCHAR(50);
ALTER TABLE documents ADD COLUMN edition_date DATE;
ALTER TABLE documents ADD COLUMN superseded BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX uq_documents_current_edition
    ON documents (act_canonical, article_number)
    WHERE document_kind = 'LEGISLATION' AND superseded = FALSE;

CREATE INDEX idx_documents_legislation_current
    ON documents (document_kind)
    WHERE document_kind = 'LEGISLATION' AND superseded = FALSE AND case_id IS NULL;
