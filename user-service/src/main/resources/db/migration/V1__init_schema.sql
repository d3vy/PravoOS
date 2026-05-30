CREATE TABLE users
(
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(50)  NOT NULL,
    status        VARCHAR(50)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE lawyer_profiles
(
    user_id        UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    full_name      VARCHAR(255) NOT NULL,
    bar_number     VARCHAR(100),
    specialization VARCHAR(255),
    phone          VARCHAR(50)
);

CREATE TABLE lawyer_applications
(
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email          VARCHAR(255) NOT NULL,
    full_name      VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    bar_number     VARCHAR(100),
    specialization VARCHAR(255),
    phone          VARCHAR(50),
    status         VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    submitted_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    reviewed_at    TIMESTAMP,
    reviewed_by    UUID
);

CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_role_status ON users (role, status);
CREATE INDEX idx_applications_status ON lawyer_applications (status);
CREATE INDEX idx_applications_email ON lawyer_applications (email);
