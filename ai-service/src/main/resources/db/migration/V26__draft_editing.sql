ALTER TABLE case_drafts ADD COLUMN updated_at TIMESTAMP;

CREATE TABLE case_draft_versions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    draft_id    UUID NOT NULL REFERENCES case_drafts(id) ON DELETE CASCADE,
    version_no  INT NOT NULL,
    content     TEXT NOT NULL,
    note        VARCHAR(500),
    created_by  UUID NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_case_draft_versions_draft_id ON case_draft_versions(draft_id);
CREATE UNIQUE INDEX uq_case_draft_versions_draft_version ON case_draft_versions(draft_id, version_no);
