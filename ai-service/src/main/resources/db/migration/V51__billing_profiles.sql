CREATE TABLE billing_profiles (
    lawyer_id       UUID PRIMARY KEY,
    name            TEXT        NOT NULL,
    inn             VARCHAR(20),
    kpp             VARCHAR(20),
    ogrn            VARCHAR(20),
    legal_address   TEXT,
    bank_name       TEXT,
    bank_bic        VARCHAR(20),
    bank_account    VARCHAR(34),
    corr_account    VARCHAR(34),
    email           TEXT,
    phone           TEXT,
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);
