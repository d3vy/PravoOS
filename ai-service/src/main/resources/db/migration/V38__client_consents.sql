CREATE TABLE client_consents
(
    id             UUID PRIMARY KEY    DEFAULT gen_random_uuid(),
    client_id      UUID        NOT NULL REFERENCES clients (id) ON DELETE CASCADE,
    lawyer_id      UUID        NOT NULL,
    policy_version VARCHAR(20) NOT NULL,
    granted_at     TIMESTAMP   NOT NULL DEFAULT NOW(),
    revoked_at     TIMESTAMP
);

CREATE INDEX idx_client_consents_client_id ON client_consents (client_id, granted_at DESC);

INSERT INTO client_consents (client_id, lawyer_id, policy_version, granted_at)
SELECT id, lawyer_id, '1.0', created_at
FROM clients;
