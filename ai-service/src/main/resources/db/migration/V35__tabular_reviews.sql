CREATE TABLE tabular_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id UUID NOT NULL,
    lawyer_id UUID NOT NULL,
    title VARCHAR(300) NOT NULL,
    status VARCHAR(20) NOT NULL,
    questions JSONB NOT NULL DEFAULT '[]'::jsonb,
    document_count INT NOT NULL,
    question_count INT NOT NULL,
    error_message VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    completed_at TIMESTAMP
);

CREATE INDEX idx_tabular_reviews_case ON tabular_reviews (case_id, created_at DESC);
CREATE INDEX idx_tabular_reviews_lawyer ON tabular_reviews (lawyer_id);

CREATE TABLE tabular_review_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id UUID NOT NULL REFERENCES tabular_reviews (id) ON DELETE CASCADE,
    document_id UUID NOT NULL,
    document_title VARCHAR(500) NOT NULL,
    position INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_message VARCHAR(500),
    CONSTRAINT uq_tabular_review_document UNIQUE (review_id, document_id)
);

CREATE INDEX idx_tabular_review_documents_review ON tabular_review_documents (review_id, position);

CREATE TABLE tabular_review_cells (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id UUID NOT NULL REFERENCES tabular_reviews (id) ON DELETE CASCADE,
    document_id UUID NOT NULL,
    question_index INT NOT NULL,
    answer TEXT NOT NULL,
    confidence VARCHAR(20) NOT NULL,
    citations JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_tabular_review_cell UNIQUE (review_id, document_id, question_index)
);

CREATE INDEX idx_tabular_review_cells_review ON tabular_review_cells (review_id);
