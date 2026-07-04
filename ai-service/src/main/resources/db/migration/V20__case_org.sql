ALTER TABLE cases ADD COLUMN org_id UUID;

CREATE INDEX idx_cases_org_status ON cases (org_id, status);
