DROP INDEX CONCURRENTLY IF EXISTS idx_case_tasks_done_due_date;
CREATE INDEX CONCURRENTLY idx_case_tasks_done_due_date
    ON case_tasks (done, due_date);

DROP INDEX CONCURRENTLY IF EXISTS idx_cases_court_case_number_active;
CREATE INDEX CONCURRENTLY idx_cases_court_case_number_active
    ON cases (court_case_number)
    WHERE deleted_at IS NULL AND court_case_number IS NOT NULL;

DROP INDEX CONCURRENTLY IF EXISTS idx_cases_filing_deadline_active;
CREATE INDEX CONCURRENTLY idx_cases_filing_deadline_active
    ON cases (filing_deadline)
    WHERE deleted_at IS NULL AND filing_deadline IS NOT NULL;

DROP INDEX CONCURRENTLY IF EXISTS idx_cases_next_hearing_date_active;
CREATE INDEX CONCURRENTLY idx_cases_next_hearing_date_active
    ON cases (next_hearing_date)
    WHERE deleted_at IS NULL AND next_hearing_date IS NOT NULL;

DROP INDEX CONCURRENTLY IF EXISTS idx_cases_expires_at_active;
CREATE INDEX CONCURRENTLY idx_cases_expires_at_active
    ON cases (expires_at)
    WHERE deleted_at IS NULL AND expires_at IS NOT NULL;

DROP INDEX CONCURRENTLY IF EXISTS idx_invoices_status_due_date_active;
CREATE INDEX CONCURRENTLY idx_invoices_status_due_date_active
    ON invoices (status, due_date)
    WHERE deleted_at IS NULL;

DROP INDEX CONCURRENTLY IF EXISTS idx_documents_uploaded_at_active;
CREATE INDEX CONCURRENTLY idx_documents_uploaded_at_active
    ON documents (uploaded_at DESC)
    WHERE deleted_at IS NULL;

DROP INDEX CONCURRENTLY IF EXISTS idx_ai_responses_created_at;
CREATE INDEX CONCURRENTLY idx_ai_responses_created_at
    ON ai_responses (created_at DESC);

DROP INDEX CONCURRENTLY IF EXISTS idx_case_drafts_case_created;
CREATE INDEX CONCURRENTLY idx_case_drafts_case_created
    ON case_drafts (case_id, created_at DESC);

DROP INDEX CONCURRENTLY IF EXISTS idx_client_contacts_client_contact_date;
CREATE INDEX CONCURRENTLY idx_client_contacts_client_contact_date
    ON client_contacts (client_id, contact_date DESC, created_at DESC);

DROP INDEX CONCURRENTLY IF EXISTS idx_time_entries_lawyer_unbilled;
CREATE INDEX CONCURRENTLY idx_time_entries_lawyer_unbilled
    ON time_entries (lawyer_id)
    WHERE billable AND invoice_id IS NULL AND running = FALSE;

CREATE EXTENSION IF NOT EXISTS pg_trgm;

DROP INDEX CONCURRENTLY IF EXISTS idx_case_parties_name_trgm;
CREATE INDEX CONCURRENTLY idx_case_parties_name_trgm
    ON case_parties USING gin (lower(name) gin_trgm_ops);
