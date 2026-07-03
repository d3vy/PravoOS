CREATE TABLE organization_invites (
    id          UUID PRIMARY KEY,
    org_id      UUID NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    email       VARCHAR(320) NOT NULL,
    org_role    VARCHAR(20) NOT NULL,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    invited_by  UUID NOT NULL REFERENCES users (id),
    status      VARCHAR(20) NOT NULL,
    expires_at  TIMESTAMP NOT NULL,
    accepted_at TIMESTAMP,
    created_at  TIMESTAMP NOT NULL
);

CREATE INDEX idx_org_invites_org_status ON organization_invites (org_id, status);
CREATE INDEX idx_org_invites_email ON organization_invites (email);
