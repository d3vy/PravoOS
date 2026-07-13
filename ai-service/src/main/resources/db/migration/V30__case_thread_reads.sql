CREATE TABLE case_thread_reads (
    case_id      UUID NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    user_id      UUID NOT NULL,
    last_read_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'utc'),
    PRIMARY KEY (case_id, user_id)
);
