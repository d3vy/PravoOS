CREATE TABLE plans (
    id             UUID PRIMARY KEY,
    code           VARCHAR(20)  NOT NULL UNIQUE,
    name           VARCHAR(100) NOT NULL,
    price_kopecks  BIGINT       NOT NULL,
    daily_requests INTEGER      NOT NULL,
    daily_tokens   BIGINT       NOT NULL,
    seats          INTEGER      NOT NULL,
    is_default     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP    NOT NULL
);

CREATE UNIQUE INDEX uq_plan_default ON plans (is_default) WHERE is_default = TRUE;

CREATE TABLE subscriptions (
    id                 UUID PRIMARY KEY,
    user_id            UUID        NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    plan_id            UUID        NOT NULL REFERENCES plans (id),
    status             VARCHAR(20) NOT NULL,
    trial_end          TIMESTAMP,
    current_period_end TIMESTAMP,
    version            BIGINT      NOT NULL DEFAULT 0,
    created_at         TIMESTAMP   NOT NULL,
    updated_at         TIMESTAMP   NOT NULL
);

CREATE INDEX idx_subscription_plan ON subscriptions (plan_id);
CREATE INDEX idx_subscription_status ON subscriptions (status);

INSERT INTO plans (id, code, name, price_kopecks, daily_requests, daily_tokens, seats, is_default, created_at) VALUES
    (gen_random_uuid(), 'FREE', 'Free',   0,       20,   20000,   1,  TRUE,  now()),
    (gen_random_uuid(), 'SOLO', 'Solo',   149000,  200,  200000,  1,  FALSE, now()),
    (gen_random_uuid(), 'TEAM', 'Team',   490000,  500,  1000000, 5,  FALSE, now()),
    (gen_random_uuid(), 'FIRM', 'Firm',   1490000, 2000, 5000000, 20, FALSE, now());
