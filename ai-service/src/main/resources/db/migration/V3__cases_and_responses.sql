CREATE TABLE cases
(
    id          UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    lawyer_id   UUID         NOT NULL,
    title       VARCHAR(500) NOT NULL,
    description TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cases_lawyer_id ON cases (lawyer_id);

ALTER TABLE documents
    ADD COLUMN case_id UUID REFERENCES cases (id) ON DELETE CASCADE;

CREATE INDEX idx_documents_case_id ON documents (case_id);

CREATE TABLE ai_responses
(
    id             UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    case_id        UUID         NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    lawyer_id      UUID         NOT NULL,
    workflow_id    VARCHAR(100) NOT NULL,
    query          TEXT         NOT NULL,
    result         TEXT         NOT NULL,
    sources        JSONB        NOT NULL DEFAULT '[]',
    rating         SMALLINT,
    rating_comment TEXT,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_responses_case_id ON ai_responses (case_id);
CREATE INDEX idx_ai_responses_workflow_id ON ai_responses (workflow_id);
CREATE INDEX idx_ai_responses_rating ON ai_responses (rating);
