ALTER TABLE cases     ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE clients   ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE documents ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE invoices  ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX idx_cases_deleted_at ON cases (deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX idx_clients_deleted_at ON clients (deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX idx_documents_deleted_at ON documents (deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX idx_invoices_deleted_at ON invoices (deleted_at) WHERE deleted_at IS NOT NULL;

CREATE TABLE deleted_entry
(
    id               UUID PRIMARY KEY,
    org_id           UUID,
    owner_id         UUID         NOT NULL,
    entity_type      VARCHAR(64)  NOT NULL,
    entity_id        VARCHAR(64)  NOT NULL,
    title            VARCHAR(512) NOT NULL,
    area             VARCHAR(32)  NOT NULL,
    deleted_by       UUID         NOT NULL,
    deleted_by_role  VARCHAR(32)  NOT NULL,
    deleted_at       TIMESTAMP    NOT NULL,
    purge_after      TIMESTAMP    NOT NULL,
    restored_at      TIMESTAMP,
    cascade_group_id UUID         NOT NULL,
    cascade_root     BOOLEAN      NOT NULL,
    payload          JSONB        NOT NULL
);

CREATE UNIQUE INDEX ux_deleted_entry_active ON deleted_entry (entity_type, entity_id)
    WHERE restored_at IS NULL;

CREATE INDEX idx_deleted_entry_owner ON deleted_entry (owner_id, deleted_at DESC)
    WHERE restored_at IS NULL;

CREATE INDEX idx_deleted_entry_org ON deleted_entry (org_id, deleted_at DESC)
    WHERE restored_at IS NULL;

CREATE INDEX idx_deleted_entry_purge ON deleted_entry (purge_after)
    WHERE restored_at IS NULL;

CREATE INDEX idx_deleted_entry_group ON deleted_entry (cascade_group_id);
