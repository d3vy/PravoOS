CREATE TABLE saved_views (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lawyer_id        UUID NOT NULL,
    org_id           UUID,
    scope            VARCHAR(30) NOT NULL,
    name             VARCHAR(80) NOT NULL,
    config           TEXT NOT NULL,
    shared_with_team BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'utc'),
    updated_at       TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'utc'),
    CONSTRAINT uq_saved_views_owner_scope_name UNIQUE (lawyer_id, scope, name)
);

CREATE INDEX idx_saved_views_owner ON saved_views (lawyer_id, scope, created_at);

CREATE INDEX idx_saved_views_team ON saved_views (org_id, scope)
    WHERE shared_with_team AND org_id IS NOT NULL;
