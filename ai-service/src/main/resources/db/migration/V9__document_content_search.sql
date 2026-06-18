CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_document_chunks_content_trgm
    ON document_chunks USING gin (lower(content) gin_trgm_ops);

CREATE INDEX idx_documents_title_trgm
    ON documents USING gin (lower(title) gin_trgm_ops);
