CREATE TABLE push_subscriptions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    endpoint     TEXT        NOT NULL UNIQUE,
    p256dh_key   TEXT        NOT NULL,
    auth_key     TEXT        NOT NULL,
    user_agent   VARCHAR(255),
    created_at   TIMESTAMP   NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMP
);

CREATE INDEX idx_push_subscriptions_user ON push_subscriptions (user_id);

ALTER TABLE users
    ADD COLUMN login_alert_push  BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN case_message_push BOOLEAN NOT NULL DEFAULT TRUE;
