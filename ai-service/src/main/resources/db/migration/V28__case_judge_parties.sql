ALTER TABLE cases ADD COLUMN arbitr_judge VARCHAR(300);

CREATE TABLE case_parties (
    id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id  UUID NOT NULL,
    name     VARCHAR(500) NOT NULL,
    role     VARCHAR(200)
);

CREATE INDEX idx_case_parties_case_id ON case_parties(case_id);
CREATE INDEX idx_case_parties_name ON case_parties(name);
