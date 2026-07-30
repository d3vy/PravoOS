CREATE TABLE invoice_payments (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id           UUID NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    provider_payment_id  VARCHAR(64) NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    amount_kopecks        BIGINT NOT NULL,
    confirmation_url     TEXT,
    created_at           TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'utc'),
    paid_at              TIMESTAMP,
    CONSTRAINT uq_invoice_payments_provider_payment_id UNIQUE (provider_payment_id)
);

CREATE INDEX idx_invoice_payments_invoice ON invoice_payments (invoice_id);

CREATE TABLE invoice_overdue_reminders (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id     UUID NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    threshold_days INT NOT NULL,
    sent_at        TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'utc'),
    CONSTRAINT uq_invoice_overdue_reminder UNIQUE (invoice_id, threshold_days)
);
