-- V1: Create users table
CREATE TABLE users (
    id                      UUID            NOT NULL DEFAULT gen_random_uuid(),
    username                VARCHAR(64)     NOT NULL,
    email                   VARCHAR(255)    NOT NULL,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    email_verified          BOOLEAN         NOT NULL DEFAULT FALSE,
    failed_login_attempts   INT             NOT NULL DEFAULT 0,
    locked_until            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED', 'DELETED'))
);

CREATE INDEX idx_users_username ON users (username);
CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_status ON users (status);
