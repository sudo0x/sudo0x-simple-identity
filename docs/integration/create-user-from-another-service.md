# How Another Service Creates a User

This guide is for any backend service (a billing service, an onboarding service, an admin panel backend, etc.) that needs to create users in the Identity Service programmatically.

---

## The core rule

There is no public registration endpoint. To create a user, your service must authenticate as a **service account** that holds the `user:create` permission. Everything else follows from that.

```
Your Service
    │
    │  1. POST /api/v1/auth/login  (service account credentials)
    │  2. Receive access token (JWT, valid 15 min)
    │  3. POST /api/v1/users       (Bearer <access_token>)
    │  4. Optionally: PUT /api/v1/users/{id}/roles/{roleId}
    ▼
Identity Service
```

---

## Step 0 — One-time setup: Create a service account

This is done once by a human admin (via the admin panel or a direct API call with the bootstrap admin token). It is not done by your service at runtime.

### Create the service account user

```
POST /api/v1/users
Authorization: Bearer <bootstrap-admin-access-token>
Content-Type: application/json

{
  "username": "svc-onboarding",
  "email": "svc-onboarding@internal.yourdomain.com",
  "password": "<strong-random-password>"
}
```

Response `201 Created`:
```json
{
  "success": true,
  "data": {
    "id": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
    "username": "svc-onboarding",
    "email": "svc-onboarding@internal.yourdomain.com",
    "status": "ACTIVE",
    "emailVerified": false,
    "roles": [],
    "createdAt": "2024-01-01T00:00:00Z",
    "updatedAt": "2024-01-01T00:00:00Z"
  }
}
```

### Assign the ADMIN role to the service account

The `ADMIN` role carries all permissions including `user:create`. Assign it:

```
PUT /api/v1/users/{svc-account-id}/roles/00000000-0000-0000-0000-000000000001
Authorization: Bearer <bootstrap-admin-access-token>
```

> **Why the ADMIN role?**
> `user:create` is held by the ADMIN role. If you prefer a least-privilege approach, create a custom role with only `user:create` (and optionally `role:update` if you also need to assign roles). Use the role API to create that custom role, then assign it to your service account.

### Store credentials in a secret manager

Store `svc-onboarding` and its password in a secret manager (AWS Secrets Manager, HashiCorp Vault, Kubernetes Secret, etc.). Your service reads them at startup — never hardcode them in source code or config files.

---

## Step 1 — Your service logs in and gets a JWT

On startup (or when the current token is about to expire), your service calls:

```
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "svc-onboarding",
  "password": "<password-from-secret-manager>"
}
```

Response `200 OK`:
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
    "refreshToken": "dGhpcyBpcyBhIHJhbmRvbSB0b2tlbg==",
    "expiresIn": 900
  },
  "timestamp": "2024-01-01T00:00:00Z"
}
```

| Field | Meaning |
|---|---|
| `accessToken` | JWT to use in every API call. Valid for `expiresIn` seconds (default 900 = 15 minutes) |
| `refreshToken` | Use this to get a new access token without re-logging in. Valid for 30 days |
| `expiresIn` | Access token lifetime in seconds |

**Cache both tokens in memory.** Do not login on every request — that is wasteful and will slow you down.

---

## Step 2 — Create the user

```
POST /api/v1/users
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "username": "john.doe",
  "email": "john@yourdomain.com",
  "password": "SecurePassword1!"
}
```

### Request field rules

| Field | Required | Rules |
|---|---|---|
| `username` | Yes | 3–64 characters, only `a-z A-Z 0-9 _ . -` |
| `email` | Yes | Valid email format, max 255 characters |
| `password` | Yes | Validated against password policy (min length, complexity) |

### Success response `201 Created`

```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "username": "john.doe",
    "email": "john@yourdomain.com",
    "status": "ACTIVE",
    "emailVerified": false,
    "roles": [],
    "createdAt": "2024-01-01T12:00:00Z",
    "updatedAt": "2024-01-01T12:00:00Z"
  }
}
```

Save the `id` — you will need it to assign a role in the next step.

---

## Step 3 — Assign the USER role (recommended)

A newly created user has no roles. Without a role they can log in, but they will have no permissions. Assign the standard `USER` role:

```
PUT /api/v1/users/{new-user-id}/roles/00000000-0000-0000-0000-000000000003
Authorization: Bearer <accessToken>
```

This endpoint returns the updated user object with the assigned role included.

> **Role IDs are stable.** They are seeded by `V10__seed_roles_permissions.sql` with fixed UUIDs, so you can hardcode them:
>
> | Role | ID |
> |---|---|
> | ADMIN | `00000000-0000-0000-0000-000000000001` |
> | MANAGER | `00000000-0000-0000-0000-000000000002` |
> | USER | `00000000-0000-0000-0000-000000000003` |

---

## Token lifecycle — how to keep the access token fresh

The access token expires in 15 minutes. Your service should handle this automatically rather than logging in again every 15 minutes.

```
Service starts
    │
    ├── Login → store accessToken + refreshToken + expiry timestamp
    │
    ├── Before each API call:
    │       if (now > expiry - 60 seconds):
    │           POST /api/v1/auth/refresh  { "refreshToken": "<stored>" }
    │           → new accessToken + new refreshToken
    │           → update stored tokens and expiry
    │
    └── Use accessToken in Authorization header
```

### Refresh call

```
POST /api/v1/auth/refresh
Content-Type: application/json

{
  "refreshToken": "<stored-refresh-token>"
}
```

Response is identical to the login response — new `accessToken`, new `refreshToken`, and `expiresIn`. The old refresh token is immediately invalidated (rotation). Always replace both stored tokens.

> **Re-login is the fallback.** If the refresh token itself expires (30 days of inactivity) or is rejected, catch the `401` and log in again with the service account credentials from your secret manager.

---

## Error responses to handle

All error responses follow the same envelope:

```json
{
  "success": false,
  "message": "...",
  "timestamp": "..."
}
```

| HTTP Status | When it happens | What to do |
|---|---|---|
| `400 Bad Request` | Validation failed (bad username format, weak password) | Fix the input. Do not retry as-is |
| `401 Unauthorized` | Access token missing, expired, or invalid | Refresh the token, then retry once |
| `403 Forbidden` | Token is valid but service account lacks `user:create` | Check the service account's role assignment |
| `409 Conflict` | Username or email already exists | The user already exists — treat as success or report duplicate |
| `422 Unprocessable Entity` | Password does not meet policy | Prompt the caller for a stronger password |

Validation errors include a field-level breakdown:

```json
{
  "success": false,
  "message": "Validation failed",
  "errors": [
    { "field": "username", "message": "Username must be 3-64 characters" },
    { "field": "password", "message": "Password must contain at least one uppercase letter" }
  ]
}
```

---

## Complete flow — all steps together

```
[One-time setup by admin]
  Create service account user
  Assign ADMIN (or custom) role to service account
  Store credentials in secret manager

[Service startup]
  Read credentials from secret manager
  POST /api/v1/auth/login
  Store accessToken, refreshToken, expiresAt

[When your business logic needs to create a user]
  if token near expiry:
      POST /api/v1/auth/refresh → update stored tokens
  POST /api/v1/users with Authorization: Bearer <accessToken>
  Save returned user.id
  PUT /api/v1/users/{id}/roles/00000000-0000-0000-0000-000000000003
```

---

## Security checklist before going to production

- [ ] Service account credentials are stored in a secret manager, not in environment variable files or source code
- [ ] The service account has only the roles it actually needs (principle of least privilege)
- [ ] Your service handles `401` gracefully: refresh → retry, or re-login → retry — never silently swallow it
- [ ] Tokens are stored in memory only, never written to disk or logs
- [ ] You validate the `username` and `email` inputs in your own service before calling the Identity Service (do not send garbage and rely on Identity Service to catch it)
- [ ] The initial user password you generate is random and strong — the user should change it on first login via the `/api/v1/password/change` endpoint

---

## Minimal pseudocode reference

```
class IdentityClient:
    accessToken = null
    refreshToken = null
    expiresAt = 0

    fn ensureToken():
        if now() > expiresAt - 60:
            if refreshToken is not null:
                resp = POST /api/v1/auth/refresh { refreshToken }
            else:
                resp = POST /api/v1/auth/login { username, password }
            accessToken = resp.data.accessToken
            refreshToken = resp.data.refreshToken
            expiresAt = now() + resp.data.expiresIn

    fn createUser(username, email, password):
        ensureToken()
        resp = POST /api/v1/users
               Authorization: Bearer <accessToken>
               { username, email, password }
        if resp.status == 401:
            refreshToken = null  // force re-login
            ensureToken()
            resp = POST /api/v1/users ... (retry once)
        return resp.data  // the created UserResponse

    fn assignUserRole(userId):
        ensureToken()
        PUT /api/v1/users/{userId}/roles/00000000-0000-0000-0000-000000000003
        Authorization: Bearer <accessToken>
```
