ALTER TABLE refresh_tokens
    ADD COLUMN ip_address   VARCHAR(45),
    ADD COLUMN user_agent   VARCHAR(255),
    ADD COLUMN last_used_at TIMESTAMP;

CREATE INDEX idx_refresh_tokens_user_ip ON refresh_tokens (user_id, ip_address);
