CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE documents
(
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title       VARCHAR(500) NOT NULL,
    file_name   VARCHAR(500) NOT NULL,
    file_type   VARCHAR(50)  NOT NULL,
    uploaded_by UUID         NOT NULL,
    uploaded_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    status      VARCHAR(50)  NOT NULL DEFAULT 'PROCESSING'
);

CREATE TABLE document_chunks
(
    id          UUID PRIMARY KEY    DEFAULT gen_random_uuid(),
    document_id UUID       NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    content     TEXT       NOT NULL,
    chunk_index INTEGER    NOT NULL,
    embedding   vector(1536),
    created_at  TIMESTAMP  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_document_chunks_document_id ON document_chunks (document_id);
CREATE INDEX idx_documents_status ON documents (status);
CREATE INDEX idx_documents_uploaded_by ON documents (uploaded_by);
