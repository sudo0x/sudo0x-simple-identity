# Module: Authentication

**Package:** `com.sudo0x.simple.identity.authentication`

Handles everything a client does to prove who they are: logging in, refreshing tokens, logging out, and retrieving the current user's identity. Also exposes the JWT public key so consuming services can validate tokens without calling back.

---

## What this module does

| Responsibility | Description |
|---|---|
| Login | Validates credentials, issues an access token + refresh token |
| Refresh | Rotates the refresh token, issues a new access token |
| Logout | Revokes the current refresh token |
| Logout-all | Revokes every refresh token for the user (all sessions) |
| /me | Returns the authenticated user's profile without a DB call beyond the JWT |
| JWKS | Exposes the RSA public key so downstream services can verify JWTs |

---

## Key classes

| Class | Role |
|---|---|
| `AuthController` | REST endpoints (`/api/v1/auth/*`) |
| `AuthService` | Core login/refresh/logout business logic |
| `JwtService` | RSA key management, JWT generation, and validation |
| `JwksController` | `GET /.well-known/jwks.json` — public key endpoint |
| `LoginRequest` | DTO — `username` + `password` |
| `LoginResponse` | DTO — `accessToken`, `refreshToken`, `tokenType`, `expiresIn` |
| `RefreshRequest` | DTO — `refreshToken` (optional when cookie auth is on) |
| `LogoutRequest` | DTO — `refreshToken` (optional when cookie auth is on) |
| `MeResponse` | DTO — `userId`, `username`, `email`, `roles`, `permissions` |

---

## Login flow (step by step)

```
POST /api/v1/auth/login
  { "username": "alice", "password": "Secret@1" }
         │
         ▼  1. Look up user by username
              If not found → dummy password check (timing attack prevention)
              → throw InvalidCredentialsException
         │
         ▼  2. Check account status
              DELETED / INACTIVE → AccountDisabledException
              LOCKED (not expired) → AccountLockedException
         │
         ▼  3. Verify Argon2id password hash
              If wrong → increment failedLoginAttempts
              If reached maxLoginAttempts → lock account
              → throw InvalidCredentialsException
         │
         ▼  4. Reset failedLoginAttempts = 0
         │
         ▼  5. Load roles + permissions (single JOIN FETCH query via @EntityGraph)
         │
         ▼  6. Generate RSA-signed JWT (RS256)
              Claims: sub=userId, username, roles[], permissions[], iss, iat, exp
         │
         ▼  7. Issue refresh token
              64-byte random → base64url → stored as SHA-256 hash only
         │
         ▼  8. Record LOGIN_SUCCESS audit event (async, non-blocking)
         │
         ▼  Return 200
              { accessToken, refreshToken, tokenType: "Bearer", expiresIn: 900 }
              + Set-Cookie headers (if cookie.enabled=true)
```

---

## Token refresh flow

```
POST /api/v1/auth/refresh
  { "refreshToken": "..." }        ← body, OR refresh_token cookie
         │
         ▼  RefreshTokenService.rotate(plaintextToken)
              Pessimistic DB lock → prevents concurrent rotation
              If token is REVOKED → reuse detected → revoke ALL user sessions
              If token is EXPIRED → InvalidRefreshTokenException
              Mark old token revoked, issue new token
         │
         ▼  Load user (check account still active)
         │
         ▼  Generate new JWT
         │
         ▼  Record TOKEN_REFRESHED audit event
         │
         ▼  Return new { accessToken, refreshToken }
              + new Set-Cookie headers (if cookie.enabled=true)
```

---

## Cookie authentication

When `identity.cookie.enabled=true`, auth endpoints also set HttpOnly cookies alongside the JSON response body. This lets browser clients authenticate without ever touching tokens in JavaScript.

**Token extraction priority (JwtAuthenticationFilter):**
1. `Authorization: Bearer <token>` header — always checked first
2. `access_token` HttpOnly cookie — fallback when `cookie.enabled=true`

**For refresh and logout via cookies:**

Browser clients call `/refresh` or `/logout` with no request body. The server reads the `refresh_token` cookie automatically. API clients (mobile, server-to-server) always pass the token in the request body.

---

## Brute force protection

| Config | Default | Description |
|---|---|---|
| `identity.security.max-login-attempts` | `5` | Failed attempts before lockout |
| `identity.security.lock-duration` | `PT15M` | Lock duration after max attempts |

After `maxLoginAttempts` failures: `user.status = LOCKED`, `lockedUntil = now + lockDuration`. The lock is automatically lifted on the next login attempt after `lockedUntil` passes — no admin action needed.

---

## User enumeration prevention

Login always returns the same generic error (`"Authentication failed"`) whether the username doesn't exist or the password is wrong. For unknown usernames, a dummy `credentialService.verify(UUID.randomUUID(), password)` is called to consume comparable CPU time, so an attacker cannot distinguish "user not found" from "wrong password" by measuring response time.

---

## Endpoints

| Method | Path | Auth required | Description |
|---|---|---|---|
| `POST` | `/api/v1/auth/login` | No | Authenticate |
| `POST` | `/api/v1/auth/refresh` | No | Refresh tokens |
| `POST` | `/api/v1/auth/logout` | Yes | Revoke current session |
| `POST` | `/api/v1/auth/logout-all` | Yes | Revoke all sessions |
| `GET` | `/api/v1/auth/me` | Yes | Current user info |
| `GET` | `/.well-known/jwks.json` | No | RSA public key (JWKS format) |
