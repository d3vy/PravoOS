CREATE TABLE ai_trust_counter_flush_attempts (
    attempt_id UUID        NOT NULL PRIMARY KEY,
    stat_date  DATE        NOT NULL,
    metric     VARCHAR(40) NOT NULL,
    delta      BIGINT      NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
