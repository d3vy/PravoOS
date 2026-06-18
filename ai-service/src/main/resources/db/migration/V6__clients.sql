CREATE TABLE clients
(
    id         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    lawyer_id  UUID         NOT NULL,
    name       VARCHAR(300) NOT NULL,
    type       VARCHAR(20)  NOT NULL,
    phone      VARCHAR(20),
    email      VARCHAR(255),
    inn        VARCHAR(12),
    notes      TEXT,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_clients_lawyer_id ON clients (lawyer_id);

ALTER TABLE cases
    ADD COLUMN client_id UUID REFERENCES clients (id) ON DELETE SET NULL;

CREATE INDEX idx_cases_client_id ON cases (client_id);
