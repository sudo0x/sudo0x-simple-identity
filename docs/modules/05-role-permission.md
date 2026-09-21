# Module: Role & Permission

**Packages:** `com.sudo0x.simple.identity.role` / `com.sudo0x.simple.identity.permission`

Implements Role-Based Access Control (RBAC). Roles are named groups; permissions are fine-grained capability codes. Users have roles; roles have permissions.

---

## What these modules do

| Module | Responsibility |
|---|---|
| `role` | CRUD for roles, assigning/removing permissions from roles |
| `permission` | Read-only listing of available permissions |

Permissions are seeded by Flyway migration (`V10`) and are not created via the API — they represent fixed capabilities that the application supports.

---

## Data model

```
User (many-to-many) ──► Role (many-to-many) ──► Permission
                          │                        │
                    e.g. "ADMIN"              e.g. "user:read"
                         "MANAGER"                 "user:create"
                         "USER"                    "role:update"
```

Tables:
- `roles` — `id`, `name` (unique), `description`
- `permissions` — `id`, `code` (unique), `description`
- `user_roles` — join table (userId, roleId)
- `role_permissions` — join table (roleId, permissionId)

---

## Seeded roles and permissions (V10 migration)

### Roles

| Role | Intended for |
|---|---|
| `ADMIN` | Full system access — all permissions |
| `MANAGER` | User management without system configuration access |
| `USER` | Read-only access to own resources |

### Permission codes

| Code | Controls access to |
|---|---|
| `user:read` | List and get users |
| `user:create` | Create new users |
| `user:update` | Update user fields and status |
| `user:delete` | Soft-delete users |
| `role:read` | List and get roles |
| `role:create` | Create new roles |
| `role:update` | Modify roles, assign/remove permissions, assign roles to users |
| `role:delete` | Delete roles |
| `permission:read` | List permissions |

---

## How permissions flow into JWTs

When a user logs in:

1. `UserRepository.findByUsernameWithRolesAndPermissions()` loads roles + permissions in one query
2. `AuthService.buildPrincipal()` extracts role names and flattens all permission codes
3. Both are embedded in the JWT claims:
   ```json
   { "roles": ["ADMIN"], "permissions": ["user:read", "user:create", "role:read", ...] }
   ```
4. `JwtAuthenticationFilter` reads these claims on every request and populates `SecurityContextHolder`

Authorities registered in Spring Security:
- Roles → `ROLE_ADMIN`, `ROLE_USER`, etc.
- Permissions → `permission:user:read`, `permission:role:create`, etc.

---

## Protecting endpoints with permissions

```java
@PreAuthorize("hasAuthority('permission:user:read')")
public ResponseEntity<...> listUsers(...) { ... }
```

Or with roles:
```java
@PreAuthorize("hasRole('ADMIN')")
```

Both styles work. The permission-based style is preferred because it is more granular and doesn't require reconfiguring code to change what a role can do.

---

## Key classes

| Class | Role |
|---|---|
| `RoleService` | CRUD for roles, permission assignment |
| `RoleController` | REST endpoints (`/api/v1/roles/**`) |
| `Role` | JPA entity — `id`, `name`, `description`, `permissions` |
| `RoleRepository` | DB queries |
| `RoleMapper` | `Role` → `RoleResponse` |
| `PermissionService` | Read-only — list all permissions |
| `PermissionController` | REST endpoint (`GET /api/v1/permissions`) |
| `Permission` | JPA entity — `id`, `code`, `description` |
| `PermissionRepository` | DB queries |

---

## Endpoints

| Method | Path | Permission needed | Description |
|---|---|---|---|
| `GET` | `/api/v1/roles` | `role:read` | List all roles |
| `GET` | `/api/v1/roles/{id}` | `role:read` | Get role by ID |
| `POST` | `/api/v1/roles` | `role:create` | Create a role |
| `PATCH` | `/api/v1/roles/{id}` | `role:update` | Update role name/description |
| `DELETE` | `/api/v1/roles/{id}` | `role:delete` | Delete a role |
| `PUT` | `/api/v1/roles/{roleId}/permissions/{permId}` | `role:update` | Assign permission to role |
| `DELETE` | `/api/v1/roles/{roleId}/permissions/{permId}` | `role:update` | Remove permission from role |
| `GET` | `/api/v1/permissions` | `permission:read` | List all permissions |

---

## Design notes

**Why are permissions read-only via API?**
Permission codes are referenced in `@PreAuthorize` annotations throughout the codebase. If permissions could be created or deleted via API, a mistype or deletion could silently break access control. Keeping them as seeded constants means the set of capabilities is always in sync with the code.

**Why no permission hierarchy?**
Flat permission codes are simple to reason about. `role:update` covers all role-modification actions rather than having `role:add-permission`, `role:remove-permission`, etc. You can make them more granular in the future by adding new permission codes and updating the annotations.
