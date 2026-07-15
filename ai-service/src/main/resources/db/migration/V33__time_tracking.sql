CREATE TABLE invoices (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lawyer_id   UUID NOT NULL,
    client_id   UUID NOT NULL REFERENCES clients (id) ON DELETE CASCADE,
    number      VARCHAR(40) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    issue_date  DATE NOT NULL,
    due_date    DATE,
    currency    VARCHAR(3) NOT NULL DEFAULT 'RUB',
    subtotal    NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total       NUMERIC(14, 2) NOT NULL DEFAULT 0,
    notes       TEXT,
    version     BIGINT NOT NULL DEFAULT 0,
    created_at  TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'utc'),
    CONSTRAINT uq_invoices_lawyer_number UNIQUE (lawyer_id, number)
);

CREATE INDEX idx_invoices_lawyer ON invoices (lawyer_id, created_at DESC);
CREATE INDEX idx_invoices_client ON invoices (client_id);

CREATE TABLE invoice_lines (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id    UUID NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    time_entry_id UUID,
    description   TEXT NOT NULL,
    minutes       INTEGER NOT NULL,
    hourly_rate   NUMERIC(12, 2) NOT NULL,
    amount        NUMERIC(14, 2) NOT NULL,
    position      INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_invoice_lines_invoice ON invoice_lines (invoice_id, position);

CREATE TABLE time_entries (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id       UUID NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    client_id     UUID REFERENCES clients (id) ON DELETE SET NULL,
    lawyer_id     UUID NOT NULL,
    description   TEXT NOT NULL,
    activity_date DATE NOT NULL,
    minutes       INTEGER NOT NULL DEFAULT 0,
    hourly_rate   NUMERIC(12, 2) NOT NULL DEFAULT 0,
    billable      BOOLEAN NOT NULL DEFAULT TRUE,
    running       BOOLEAN NOT NULL DEFAULT FALSE,
    started_at    TIMESTAMP,
    invoice_id    UUID REFERENCES invoices (id) ON DELETE SET NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'utc')
);

CREATE INDEX idx_time_entries_case ON time_entries (case_id, activity_date DESC);
CREATE INDEX idx_time_entries_lawyer ON time_entries (lawyer_id);
CREATE INDEX idx_time_entries_billable ON time_entries (client_id)
    WHERE invoice_id IS NULL AND billable;

CREATE UNIQUE INDEX uq_time_entries_running_lawyer ON time_entries (lawyer_id)
    WHERE running;
