-- V9: Create audit_events table (security audit log)
CREATE TABLE audit_events (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id     UUID,                   -- NULL for events with no associated user (e.g. failed login for unknown user)
    event_type  VARCHAR(64) NOT NULL,
    details     TEXT,
    ip_address  VARCHAR(45),
    user_agent  VARCHAR(512),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_audit_events PRIMARY KEY (id)
    -- No FK on user_id: audit records should persist even after user deletion
);

CREATE INDEX idx_audit_events_user_id     ON audit_events (user_id);
CREATE INDEX idx_audit_events_event_type  ON audit_events (event_type);
CREATE INDEX idx_audit_events_occurred_at ON audit_events (occurred_at DESC);
