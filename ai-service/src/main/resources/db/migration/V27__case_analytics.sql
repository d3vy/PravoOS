CREATE TABLE case_analyses (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id      UUID NOT NULL,
    lawyer_id    UUID NOT NULL,
    content      TEXT NOT NULL,
    hearing_count INT NOT NULL DEFAULT 0,
    total_tokens BIGINT NOT NULL DEFAULT 0,
    generated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_case_analyses_case ON case_analyses(case_id);
