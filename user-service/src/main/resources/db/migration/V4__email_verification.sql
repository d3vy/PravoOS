ALTER TABLE lawyer_applications
    ADD COLUMN email_verification_token  VARCHAR(255) UNIQUE,
    ADD COLUMN email_verification_expires_at TIMESTAMP,
    ADD COLUMN email_verified            BOOLEAN NOT NULL DEFAULT false;
