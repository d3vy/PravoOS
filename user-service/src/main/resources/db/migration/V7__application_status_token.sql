ALTER TABLE lawyer_applications ADD COLUMN status_token VARCHAR(64);

UPDATE lawyer_applications
SET status_token = replace(gen_random_uuid()::text, '-', '')
WHERE status_token IS NULL;

ALTER TABLE lawyer_applications ALTER COLUMN status_token SET NOT NULL;

ALTER TABLE lawyer_applications
    ADD CONSTRAINT uq_lawyer_applications_status_token UNIQUE (status_token);
