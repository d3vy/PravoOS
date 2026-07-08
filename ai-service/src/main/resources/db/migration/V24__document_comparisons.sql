CREATE TABLE document_comparisons (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id UUID NOT NULL,
    base_document_id UUID NOT NULL,
    revised_document_id UUID NOT NULL,
    lawyer_id UUID NOT NULL,
    base_document_title VARCHAR(500) NOT NULL,
    revised_document_title VARCHAR(500) NOT NULL,
    summary TEXT NOT NULL,
    risk_score SMALLINT NOT NULL,
    change_count INT NOT NULL,
    high_risk_count INT NOT NULL,
    changes JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_document_comparisons_case ON document_comparisons (case_id, created_at DESC);
CREATE INDEX idx_document_comparisons_lawyer ON document_comparisons (lawyer_id);
