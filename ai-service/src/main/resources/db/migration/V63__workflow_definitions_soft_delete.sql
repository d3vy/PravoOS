ALTER TABLE workflow_definitions ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX idx_workflow_definitions_deleted_at ON workflow_definitions (deleted_at)
    WHERE deleted_at IS NOT NULL;
