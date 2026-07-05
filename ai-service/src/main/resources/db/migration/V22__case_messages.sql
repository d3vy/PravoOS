CREATE TABLE case_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id         UUID NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    author_user_id  UUID NOT NULL,
    author_role     VARCHAR(20) NOT NULL,
    body            TEXT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'utc')
);

CREATE INDEX idx_case_messages_case_created ON case_messages (case_id, created_at);
