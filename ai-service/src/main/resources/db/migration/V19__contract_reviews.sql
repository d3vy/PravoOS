CREATE TABLE contract_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id UUID NOT NULL,
    document_id UUID NOT NULL,
    lawyer_id UUID NOT NULL,
    document_title VARCHAR(500) NOT NULL,
    summary TEXT NOT NULL,
    risk_score SMALLINT NOT NULL,
    high_risk_count INT NOT NULL,
    findings JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_contract_reviews_document ON contract_reviews (document_id, created_at DESC);
CREATE INDEX idx_contract_reviews_case ON contract_reviews (case_id, created_at DESC);
CREATE INDEX idx_contract_reviews_lawyer ON contract_reviews (lawyer_id);
