ALTER TABLE lawyer_applications ALTER COLUMN status_token TYPE VARCHAR(128);

ALTER TABLE lawyer_applications ADD COLUMN status_token_expires_at TIMESTAMP;

UPDATE lawyer_applications
SET status_token_expires_at = submitted_at + INTERVAL '30 days'
WHERE status_token_expires_at IS NULL;
