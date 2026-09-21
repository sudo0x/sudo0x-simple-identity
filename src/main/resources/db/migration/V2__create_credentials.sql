-- V2: Create credentials table (passwords stored separately from users)
CREATE TABLE credentials (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL,
    password_hash   TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_credentials PRIMARY KEY (id),
    CONSTRAINT uq_credentials_user_id UNIQUE (user_id),
    CONSTRAINT fk_credentials_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_credentials_user_id ON credentials (user_id);
