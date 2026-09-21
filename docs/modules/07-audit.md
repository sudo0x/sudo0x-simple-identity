# Module: Audit

**Package:** `com.sudo0x.simple.identity.audit`

Records security-relevant events to an immutable audit log. Every significant action — login, logout, password change, role assignment, account lock — is written here with a timestamp, who did it, who it affected, and from where.

---

## What this module does

| Responsibility | Description |
|---|---|
| Record events | Async, non-blocking write to `audit_events` table |
| Query events | Admins can search audit history by userId or event type |

---

## Key classes

| Class | Role |
|---|---|
| `AuditService` | Records events; `@Async` + `REQUIRES_NEW` transaction |
| `AuditEvent` | JPA entity — one row per event |
| `AuditEventType` | Enum — every possible event type |
| `AuditEventRepository` | DB queries including search by userId/type |

---

## Why async?

`AuditService.record(...)` is annotated `@Async`. This means:

- The calling thread (e.g., the login request) does **not** wait for the audit write to finish
- If the audit DB write is slow or fails, it does **not** fail the login
- The audit write happens in a **new transaction** (`REQUIRES_NEW`) so the main transaction's commit/rollback doesn't affect it

```
Login request thread
        │
        ├── credentialService.verify(...)   ← same transaction
        ├── refreshTokenService.issue(...)  ← same transaction
        │
        │ main transaction commits → login succeeds
        │
        └── auditService.record(LOGIN_SUCCESS) → fire and forget
              (runs on a separate thread, separate transaction)
```

This design means:
- Audit is **best-effort** — it will almost always succeed, but a failure won't crash the user's login
- If strict audit guarantees are required (e.g., compliance), replace `@Async` with synchronous writing in the same transaction

---

## AuditEvent entity fields

| Field | Type | Description |
|---|---|---|
| `id` | UUID | Primary key |
| `userId` | UUID | Who the event is about (nullable for system events) |
| `actorId` | UUID | Who caused the event (may differ from userId for admin actions) |
| `eventType` | AuditEventType | See event types below |
| `detail` | String | Human-readable context (e.g., "Locked after 5 failed attempts") |
| `ipAddress` | String | Client IP from `X-Forwarded-For` or `RemoteAddr` |
| `userAgent` | String | Truncated to 256 chars |
| `createdAt` | Instant | When it happened |

**No FK on `userId`:** The `userId` column is a plain `UUID`, not a foreign key. This is intentional — if a user is soft-deleted or even physically removed, the audit record must remain intact. The audit trail must outlive the user record.

---

## Event types

| Event | When it fires |
|---|---|
| `LOGIN_SUCCESS` | Successful login |
| `LOGIN_FAILURE` | Wrong password or unknown username |
| `ACCOUNT_LOCKED` | Account locked after too many failures |
| `ACCOUNT_UNLOCKED` | Account manually unlocked by admin |
| `LOGOUT` | Single session logout |
| `LOGOUT_ALL` | All sessions revoked |
| `TOKEN_REFRESHED` | Refresh token rotated |
| `PASSWORD_CHANGED` | User changed own password |
| `PASSWORD_RESET_REQUESTED` | Forgot-password email requested |
| `PASSWORD_RESET` | Password reset completed |
| `EMAIL_VERIFIED` | Email address verified |
| `USER_CREATED` | New user registered |
| `USER_UPDATED` | User fields updated |
| `USER_DELETED` | User soft-deleted |
| `ROLE_ASSIGNED` | Role added to user |
| `ROLE_REMOVED` | Role removed from user |

---

## Querying the audit log

The `AuditEventRepository` supports filtering by `userId` and `eventType`. Typical admin use cases:

```
GET /api/v1/audit?userId=<uuid>              ← all events for a user
GET /api/v1/audit?eventType=LOGIN_FAILURE    ← all failed logins
GET /api/v1/audit?userId=<uuid>&eventType=ACCOUNT_LOCKED
```

(The audit query endpoint is accessible to users with the `ADMIN` role.)

---

## Design notes

**Why not use a separate audit database?**
For most deployments, a table in the same PostgreSQL instance is sufficient. The audit table is append-only in practice (no updates or deletes), which makes it fast. For compliance-grade immutability, the table could be migrated to a write-once store (e.g., AWS QLDB) without changing the `AuditService` interface.

**Why not use structured logging instead of a DB table?**
Log files are easy to lose, rotate, or accidentally exclude from SIEM pipelines. A database table is queryable, survives log rotation, and is easier to expose to an admin UI.
