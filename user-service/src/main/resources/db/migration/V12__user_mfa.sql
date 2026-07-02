CREATE TABLE user_mfa
(
    user_id      UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    secret       VARCHAR(64) NOT NULL,
    enabled      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP   NOT NULL DEFAULT NOW(),
    confirmed_at TIMESTAMP
);
