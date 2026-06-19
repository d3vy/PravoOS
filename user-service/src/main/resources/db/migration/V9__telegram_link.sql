ALTER TABLE lawyer_profiles ADD COLUMN telegram_chat_id BIGINT;

CREATE TABLE telegram_link_codes (
    code        VARCHAR(64) PRIMARY KEY,
    user_id     UUID NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    expires_at  TIMESTAMP NOT NULL,
    created_at  TIMESTAMP NOT NULL
);

CREATE INDEX idx_telegram_link_codes_expires_at ON telegram_link_codes (expires_at);
