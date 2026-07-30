CREATE TABLE lawyer_digest_sent (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lawyer_id   UUID NOT NULL,
    digest_date DATE NOT NULL,
    sent_at     TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'utc'),
    CONSTRAINT uq_lawyer_digest_sent UNIQUE (lawyer_id, digest_date)
);
