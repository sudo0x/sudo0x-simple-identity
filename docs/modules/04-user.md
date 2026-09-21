# Module: User

**Package:** `com.sudo0x.simple.identity.user`

Manages user accounts: creating them, reading them, updating them, assigning roles, and soft-deleting them. This is the central entity of the system.

---

## What this module does

| Responsibility | Description |
|---|---|
| Create user | Register a new user with a password, validate uniqueness |
| Read / search | Get a user by ID, or search/filter with pagination |
| Update user | Change username, email, status |
| Delete user | Soft-delete (status → DELETED, not physically removed) |
| Assign role | Add a role to a user |
| Remove role | Remove a role from a user |

---

## User lifecycle (status states)

```
                  ┌─────────┐
  registration → │  ACTIVE  │ ← lock expires / admin unlock
                  └────┬────┘
                       │ N failed logins (> maxLoginAttempts)
                       ▼
                  ┌─────────┐
                  │  LOCKED  │
                  └────┬────┘
                       │ lock duration expires
                       ▼ (unlocked automatically on next login attempt)

  admin action → ┌──────────┐
                  │ INACTIVE  │
                  └──────────┘

  soft delete  → ┌──────────┐
                  │  DELETED  │  (permanent — cannot be undone without DB access)
                  └──────────┘
```

**Why soft delete?** Physically removing a user would break the audit log, which records events by `userId`. Keeping `DELETED` rows means the audit trail remains intact. Deleted users never appear in search results.

---

## Key classes

| Class | Role |
|---|---|
| `UserService` | Business logic for all user operations |
| `UserController` | REST endpoints (`/api/v1/users/**`) |
| `User` | JPA entity — the central domain object |
| `UserRepository` | DB queries, including search with dynamic filters |
| `UserMapper` | Converts `User` → `UserResponse` (no sensitive data exposed) |
| `CreateUserRequest` | DTO — `username`, `email`, `password` |
| `UpdateUserRequest` | DTO — `username`, `email`, `status` (all optional) |
| `UserResponse` | DTO — safe to return to clients (no password hash) |

---

## User entity fields

| Field | Type | Description |
|---|---|---|
| `id` | UUID | Primary key (auto-generated) |
| `username` | String | Unique, case-sensitive login name |
| `email` | String | Unique, lowercased on save |
| `status` | UserStatus | ACTIVE / INACTIVE / LOCKED / DELETED |
| `emailVerified` | boolean | Set by the email verification flow |
| `failedLoginAttempts` | int | Resets to 0 on successful login |
| `lockedUntil` | Instant | Null unless LOCKED |
| `roles` | Set\<Role\> | Many-to-many via `user_roles` table |
| `createdAt` | Instant | Set by `BaseEntity` auditing on first save |
| `updatedAt` | Instant | Set by `BaseEntity` auditing on every save |

---

## N+1 prevention

Loading a user's roles and permissions triggers eager loading via a named `@EntityGraph`:

```java
@EntityGraph(attributePaths = {"roles", "roles.permissions"})
Optional<User> findByUsernameWithRolesAndPermissions(String username);
```

This produces a single SQL JOIN instead of separate queries for roles and permissions. All places that need roles + permissions (login, token refresh, `/me`) use this method.

---

## Search and pagination

```
GET /api/v1/users?username=ali&email=@example.com&status=ACTIVE&page=0&size=20
```

The search query:
- Filters by username (partial, case-insensitive LIKE)
- Filters by email (partial, case-insensitive LIKE)
- Filters by status
- Always excludes DELETED users (not overridable by clients)
- Returns `PagedResponse<UserResponse>` with total count and page metadata

---

## Access control

All user endpoints require authentication plus a specific permission:

| Operation | Required permission |
|---|---|
| List / Get | `user:read` |
| Create | `user:create` |
| Update | `user:update` |
| Delete | `user:delete` |
| Assign / remove role | `role:update` |

Permissions are enforced by `@PreAuthorize` on each controller method, backed by Spring Security's method security.

---

## Audit trail

Every user operation (create, update, delete, role assignment, role removal) is recorded via `AuditService`. The audit record stores `actorId` (who made the change) and `targetUserId` (who was changed), so you can always answer "who changed what and when."
