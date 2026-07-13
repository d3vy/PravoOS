CREATE TABLE payments (
    id                  UUID PRIMARY KEY,
    user_id             UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    plan_id             UUID         NOT NULL REFERENCES plans (id),
    provider_payment_id VARCHAR(64)  NOT NULL UNIQUE,
    amount_kopecks      BIGINT       NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    confirmation_url    VARCHAR(512),
    paid_at             TIMESTAMP,
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP    NOT NULL
);

CREATE INDEX idx_payment_user ON payments (user_id, created_at DESC);
CREATE INDEX idx_payment_status ON payments (status);
