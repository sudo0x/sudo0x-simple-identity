-- V7: Create password_reset_tokens table
CREATE TABLE password_reset_tokens (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,   -- SHA-256 hex of the plaintext token
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,            -- NULL means not yet used
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id),
    CONSTRAINT uq_password_reset_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_password_reset_tokens_user_id    ON password_reset_tokens (user_id);
CREATE INDEX idx_password_reset_tokens_token_hash ON password_reset_tokens (token_hash);
