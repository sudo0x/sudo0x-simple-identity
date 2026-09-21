-- V3: Create roles and permissions tables
CREATE TABLE roles (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uq_roles_name UNIQUE (name)
);

CREATE INDEX idx_roles_name ON roles (name);

CREATE TABLE permissions (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    code        VARCHAR(128) NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_permissions PRIMARY KEY (id),
    CONSTRAINT uq_permissions_code UNIQUE (code)
);

CREATE INDEX idx_permissions_code ON permissions (code);
