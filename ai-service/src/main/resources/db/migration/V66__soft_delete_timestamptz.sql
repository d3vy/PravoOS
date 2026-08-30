ALTER TABLE cases               ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC';
ALTER TABLE clients             ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC';
ALTER TABLE documents           ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC';
ALTER TABLE invoices            ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC';
ALTER TABLE document_templates  ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC';
ALTER TABLE saved_views         ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC';
ALTER TABLE workflow_definitions ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC';
ALTER TABLE mailboxes           ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC';

ALTER TABLE deleted_entry ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC';
ALTER TABLE deleted_entry ALTER COLUMN purge_after TYPE TIMESTAMPTZ USING purge_after AT TIME ZONE 'UTC';
ALTER TABLE deleted_entry ALTER COLUMN restored_at TYPE TIMESTAMPTZ USING restored_at AT TIME ZONE 'UTC';
