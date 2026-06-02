CREATE TABLE case_drafts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id     UUID NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    lawyer_id   UUID NOT NULL,
    draft_type  VARCHAR(100) NOT NULL,
    title       VARCHAR(500) NOT NULL,
    content     TEXT NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_case_drafts_case_id ON case_drafts(case_id);
