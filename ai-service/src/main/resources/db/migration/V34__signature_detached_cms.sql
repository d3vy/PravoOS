ALTER TABLE signature_requests
    ADD COLUMN signature_data          TEXT,
    ADD COLUMN signature_file_name     VARCHAR(300),
    ADD COLUMN signature_algorithm     VARCHAR(120),
    ADD COLUMN certificate_subject     VARCHAR(1000),
    ADD COLUMN certificate_issuer      VARCHAR(1000),
    ADD COLUMN certificate_serial      VARCHAR(100),
    ADD COLUMN certificate_valid_from  TIMESTAMP,
    ADD COLUMN certificate_valid_to    TIMESTAMP,
    ADD COLUMN declared_signing_time   TIMESTAMP;

CREATE INDEX idx_signature_requests_pending_expiry
    ON signature_requests (expires_at)
    WHERE status = 'PENDING';
