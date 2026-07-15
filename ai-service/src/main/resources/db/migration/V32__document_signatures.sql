CREATE TABLE signature_requests (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id       UUID         NOT NULL,
    case_id           UUID         NOT NULL,
    signer_client_id  UUID         NOT NULL,
    requested_by      UUID         NOT NULL,
    provider          VARCHAR(30)  NOT NULL,
    external_id       VARCHAR(200),
    status            VARCHAR(20)  NOT NULL,
    document_hash     VARCHAR(64)  NOT NULL,
    message           VARCHAR(1000),
    signer_name       VARCHAR(300),
    signer_user_id    UUID,
    signer_ip         VARCHAR(64),
    signer_user_agent VARCHAR(500),
    consent_text      VARCHAR(1000),
    signed_at         TIMESTAMP,
    decline_reason    VARCHAR(1000),
    expires_at        TIMESTAMP,
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP,
    version           BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_signature_requests_case ON signature_requests (case_id);
CREATE INDEX idx_signature_requests_document ON signature_requests (document_id);
CREATE INDEX idx_signature_requests_signer_status ON signature_requests (signer_client_id, status);

CREATE UNIQUE INDEX uq_signature_requests_pending
    ON signature_requests (document_id, signer_client_id)
    WHERE status = 'PENDING';
