CREATE TABLE case_tasks
(
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id    UUID    NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    text       TEXT    NOT NULL,
    due_date   DATE,
    done       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_case_tasks_case_id ON case_tasks (case_id);
