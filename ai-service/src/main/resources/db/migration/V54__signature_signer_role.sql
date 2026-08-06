ALTER TABLE signature_requests
    ADD COLUMN signer_role      VARCHAR(20) NOT NULL DEFAULT 'CLIENT',
    ADD COLUMN signer_lawyer_id UUID;

ALTER TABLE signature_requests
    ALTER COLUMN signer_client_id DROP NOT NULL;

ALTER TABLE signature_requests
    ADD CONSTRAINT chk_signature_requests_signer CHECK (
        (signer_role = 'CLIENT' AND signer_client_id IS NOT NULL)
            OR (signer_role = 'LAWYER' AND signer_lawyer_id IS NOT NULL));

DROP INDEX uq_signature_requests_pending;

CREATE UNIQUE INDEX uq_signature_requests_pending
    ON signature_requests (document_id, signer_role, COALESCE(signer_client_id, signer_lawyer_id))
    WHERE status = 'PENDING';

CREATE INDEX idx_signature_requests_signer_lawyer
    ON signature_requests (signer_lawyer_id, status);
