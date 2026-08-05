ALTER TABLE cases RENAME COLUMN arbitr_case_number TO court_case_number;
ALTER TABLE cases RENAME COLUMN arbitr_case_guid TO court_case_guid;
ALTER TABLE cases RENAME COLUMN arbitr_judge TO judge_name;

ALTER INDEX idx_cases_arbitr_number RENAME TO idx_cases_court_number;

ALTER TABLE cases ADD COLUMN court_system VARCHAR(30) NOT NULL DEFAULT 'ARBITR';
