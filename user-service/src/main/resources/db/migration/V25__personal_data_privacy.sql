ALTER TABLE lawyer_profiles ALTER COLUMN full_name TYPE TEXT;
ALTER TABLE lawyer_profiles ALTER COLUMN phone TYPE TEXT;
ALTER TABLE lawyer_applications ALTER COLUMN full_name TYPE TEXT;
ALTER TABLE lawyer_applications ALTER COLUMN phone TYPE TEXT;

ALTER TABLE lawyer_applications
    ADD COLUMN consent_policy_version VARCHAR(20),
    ADD COLUMN consent_granted_at     TIMESTAMP,
    ADD COLUMN consent_ip             VARCHAR(64),
    ADD COLUMN consent_user_agent     TEXT,
    ADD COLUMN consent_cross_border   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN consent_marketing      BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE user_consents
(
    id             UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    user_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose        VARCHAR(40) NOT NULL,
    policy_version VARCHAR(20) NOT NULL,
    granted_at     TIMESTAMP   NOT NULL DEFAULT NOW(),
    revoked_at     TIMESTAMP,
    ip_address     VARCHAR(64),
    user_agent     TEXT,
    source         VARCHAR(40) NOT NULL DEFAULT 'SIGNUP'
);

CREATE INDEX idx_user_consents_user ON user_consents (user_id, purpose, granted_at DESC);
CREATE UNIQUE INDEX uq_user_consents_active ON user_consents (user_id, purpose) WHERE revoked_at IS NULL;

CREATE TABLE subject_requests
(
    id           UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL,
    subject_ref  VARCHAR(255) NOT NULL,
    type         VARCHAR(40) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP,
    due_at       TIMESTAMP   NOT NULL,
    ip_address   VARCHAR(64),
    note         TEXT
);

CREATE INDEX idx_subject_requests_status ON subject_requests (status, due_at);
CREATE INDEX idx_subject_requests_user ON subject_requests (user_id, requested_at DESC);

INSERT INTO user_consents (user_id, purpose, policy_version, granted_at, source)
SELECT id, 'PERSONAL_DATA', '1.0', created_at, 'LEGACY'
FROM users;
