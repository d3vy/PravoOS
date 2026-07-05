CREATE TABLE client_portal_invites (
    id          UUID PRIMARY KEY,
    client_id   UUID NOT NULL,
    lawyer_id   UUID NOT NULL REFERENCES users (id),
    email       VARCHAR(320) NOT NULL,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    status      VARCHAR(20) NOT NULL,
    user_id     UUID REFERENCES users (id) ON DELETE CASCADE,
    expires_at  TIMESTAMP NOT NULL,
    accepted_at TIMESTAMP,
    created_at  TIMESTAMP NOT NULL
);

CREATE INDEX idx_client_portal_invites_client_status ON client_portal_invites (client_id, status);
CREATE INDEX idx_client_portal_invites_user ON client_portal_invites (user_id) WHERE status = 'ACCEPTED';
