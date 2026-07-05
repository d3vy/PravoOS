ALTER TABLE documents ADD COLUMN visible_to_client BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_documents_case_visible ON documents (case_id) WHERE visible_to_client = TRUE;
