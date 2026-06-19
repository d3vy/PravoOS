ALTER TABLE cases ADD COLUMN arbitr_case_number VARCHAR(50);
ALTER TABLE cases ADD COLUMN arbitr_case_guid   VARCHAR(40);

CREATE INDEX idx_cases_arbitr_number ON cases (arbitr_case_number) WHERE arbitr_case_number IS NOT NULL;

CREATE TABLE case_hearing_events
(
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id         UUID         NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    source_event_id VARCHAR(200) NOT NULL,
    event_date      DATE,
    event_type      VARCHAR(300),
    description     TEXT,
    court_name      VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (case_id, source_event_id)
);

CREATE INDEX idx_case_hearing_events_case ON case_hearing_events (case_id, event_date DESC);
