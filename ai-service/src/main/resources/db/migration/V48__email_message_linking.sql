ALTER TABLE email_messages
    ADD COLUMN link_source       VARCHAR(20),
    ADD COLUMN linked_at         TIMESTAMP,
    ADD COLUMN client_contact_id UUID REFERENCES client_contacts (id) ON DELETE SET NULL;

CREATE UNIQUE INDEX idx_email_messages_contact ON email_messages (client_contact_id)
    WHERE client_contact_id IS NOT NULL;
