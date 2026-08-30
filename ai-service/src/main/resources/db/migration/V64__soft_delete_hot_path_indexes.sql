DROP INDEX CONCURRENTLY IF EXISTS idx_cases_deleted_at;
DROP INDEX CONCURRENTLY IF EXISTS idx_clients_deleted_at;
DROP INDEX CONCURRENTLY IF EXISTS idx_documents_deleted_at;
DROP INDEX CONCURRENTLY IF EXISTS idx_invoices_deleted_at;
DROP INDEX CONCURRENTLY IF EXISTS idx_document_templates_deleted_at;
DROP INDEX CONCURRENTLY IF EXISTS idx_saved_views_deleted_at;
DROP INDEX CONCURRENTLY IF EXISTS idx_workflow_definitions_deleted_at;
DROP INDEX CONCURRENTLY IF EXISTS idx_workflow_definitions_system;

DROP INDEX CONCURRENTLY IF EXISTS idx_cases_lawyer_active;
CREATE INDEX CONCURRENTLY idx_cases_lawyer_active ON cases (lawyer_id, created_at DESC)
    WHERE deleted_at IS NULL;

DROP INDEX CONCURRENTLY IF EXISTS idx_cases_lawyer_status_active;
CREATE INDEX CONCURRENTLY idx_cases_lawyer_status_active
    ON cases (lawyer_id, status, created_at DESC)
    WHERE deleted_at IS NULL;

DROP INDEX CONCURRENTLY IF EXISTS idx_cases_client_active;
CREATE INDEX CONCURRENTLY idx_cases_client_active ON cases (client_id, created_at DESC)
    WHERE deleted_at IS NULL;

DROP INDEX CONCURRENTLY IF EXISTS idx_cases_org_status_active;
CREATE INDEX CONCURRENTLY idx_cases_org_status_active ON cases (org_id, status)
    WHERE deleted_at IS NULL AND org_id IS NOT NULL;

DROP INDEX CONCURRENTLY IF EXISTS idx_clients_lawyer_active;
CREATE INDEX CONCURRENTLY idx_clients_lawyer_active ON clients (lawyer_id, created_at DESC)
    WHERE deleted_at IS NULL;

DROP INDEX CONCURRENTLY IF EXISTS idx_documents_case_active;
CREATE INDEX CONCURRENTLY idx_documents_case_active ON documents (case_id, uploaded_at DESC)
    WHERE deleted_at IS NULL;

DROP INDEX CONCURRENTLY IF EXISTS idx_documents_uploaded_by_active;
CREATE INDEX CONCURRENTLY idx_documents_uploaded_by_active
    ON documents (uploaded_by, document_kind)
    WHERE deleted_at IS NULL;

DROP INDEX CONCURRENTLY IF EXISTS idx_invoices_lawyer_active;
CREATE INDEX CONCURRENTLY idx_invoices_lawyer_active ON invoices (lawyer_id, created_at DESC)
    WHERE deleted_at IS NULL;

DROP INDEX CONCURRENTLY IF EXISTS idx_invoices_client_active;
CREATE INDEX CONCURRENTLY idx_invoices_client_active ON invoices (client_id, created_at DESC)
    WHERE deleted_at IS NULL;

DROP INDEX CONCURRENTLY IF EXISTS idx_document_templates_lawyer_active;
CREATE INDEX CONCURRENTLY idx_document_templates_lawyer_active
    ON document_templates (lawyer_id, created_at DESC)
    WHERE deleted_at IS NULL;

DROP INDEX CONCURRENTLY IF EXISTS idx_saved_views_owner_active;
CREATE INDEX CONCURRENTLY idx_saved_views_owner_active
    ON saved_views (lawyer_id, scope, created_at)
    WHERE deleted_at IS NULL;

DROP INDEX CONCURRENTLY IF EXISTS idx_saved_views_team_active;
CREATE INDEX CONCURRENTLY idx_saved_views_team_active ON saved_views (org_id, scope, created_at)
    WHERE deleted_at IS NULL AND shared_with_team AND org_id IS NOT NULL;

DROP INDEX CONCURRENTLY IF EXISTS idx_workflow_definitions_created_by_active;
CREATE INDEX CONCURRENTLY idx_workflow_definitions_created_by_active
    ON workflow_definitions (created_by)
    WHERE deleted_at IS NULL AND created_by IS NOT NULL;

DROP INDEX CONCURRENTLY IF EXISTS idx_workflow_definitions_org_active;
CREATE INDEX CONCURRENTLY idx_workflow_definitions_org_active ON workflow_definitions (org_id)
    WHERE deleted_at IS NULL AND org_id IS NOT NULL;

DROP INDEX CONCURRENTLY IF EXISTS idx_workflow_definitions_system_active;
CREATE INDEX CONCURRENTLY idx_workflow_definitions_system_active
    ON workflow_definitions (category, name)
    WHERE deleted_at IS NULL AND is_system;
