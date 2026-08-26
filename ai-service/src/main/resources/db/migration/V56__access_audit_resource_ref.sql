ALTER TABLE access_audit ADD COLUMN resource_ref VARCHAR(64);

CREATE INDEX idx_access_audit_resource_ref ON access_audit (resource_type, resource_ref)
    WHERE resource_ref IS NOT NULL;
