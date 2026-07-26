ALTER TABLE documents
    ADD COLUMN summary TEXT,
    ADD COLUMN summary_key_points TEXT,
    ADD COLUMN summary_status VARCHAR(50) NOT NULL DEFAULT 'NONE',
    ADD COLUMN summary_generated_at TIMESTAMP;
