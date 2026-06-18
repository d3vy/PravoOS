ALTER TABLE cases
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'INTAKE';

CREATE INDEX idx_cases_lawyer_status ON cases (lawyer_id, status);
