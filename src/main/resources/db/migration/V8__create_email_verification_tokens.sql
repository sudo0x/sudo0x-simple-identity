-- V8: Create email_verification_tokens table
CREATE TABLE email_verification_tokens (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,   -- SHA-256 hex of the plaintext token
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_email_verification_tokens PRIMARY KEY (id),
    CONSTRAINT uq_email_verification_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_email_verification_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_email_verification_tokens_user_id    ON email_verification_tokens (user_id);
CREATE INDEX idx_email_verification_tokens_token_hash ON email_verification_tokens (token_hash);
