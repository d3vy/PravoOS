CREATE TABLE access_audit
(
    id            UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    actor_id      UUID        NOT NULL,
    actor_role    VARCHAR(30) NOT NULL,
    action        VARCHAR(50) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id   UUID,
    ip_address    VARCHAR(45),
    user_agent    VARCHAR(500),
    created_at    TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_access_audit_actor_id ON access_audit (actor_id, created_at DESC);
CREATE INDEX idx_access_audit_resource ON access_audit (resource_type, resource_id);
