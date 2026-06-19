CREATE TABLE case_deadline_reminders (
    id             UUID PRIMARY KEY,
    case_id        UUID NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    deadline_type  VARCHAR(30) NOT NULL,
    deadline_date  DATE NOT NULL,
    threshold_days INT NOT NULL,
    sent_at        TIMESTAMP NOT NULL,
    CONSTRAINT uq_case_deadline_reminder UNIQUE (case_id, deadline_type, deadline_date, threshold_days)
);
