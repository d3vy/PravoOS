CREATE TABLE client_contacts (
    id           UUID PRIMARY KEY,
    client_id    UUID NOT NULL REFERENCES clients (id) ON DELETE CASCADE,
    type         VARCHAR(20) NOT NULL,
    contact_date DATE NOT NULL,
    notes        TEXT,
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_client_contacts_client ON client_contacts (client_id);
