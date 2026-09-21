-- V5: Create role_permissions join table
CREATE TABLE role_permissions (
    role_id       UUID NOT NULL,
    permission_id UUID NOT NULL,

    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role       FOREIGN KEY (role_id)       REFERENCES roles (id)       ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
);

CREATE INDEX idx_role_permissions_role_id       ON role_permissions (role_id);
CREATE INDEX idx_role_permissions_permission_id ON role_permissions (permission_id);
