CREATE TABLE organizations (
    id         UUID PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    owner_id   UUID NOT NULL REFERENCES users (id),
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE organization_memberships (
    id         UUID PRIMARY KEY,
    org_id     UUID NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    org_role   VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_membership_org_user UNIQUE (org_id, user_id)
);

CREATE INDEX idx_membership_org ON organization_memberships (org_id);
CREATE INDEX idx_membership_user ON organization_memberships (user_id);
