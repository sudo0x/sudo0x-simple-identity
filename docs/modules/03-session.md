# Module: Session

**Package:** `com.sudo0x.simple.identity.session`

Manages refresh tokens. A refresh token is a long-lived secret that a client holds and exchanges for a new short-lived JWT access token when the current one expires.

---

## What this module does

| Responsibility | Description |
|---|---|
| Issue | Create a new refresh token for a user after successful login |
| Rotate | Accept an old token, validate it, revoke it, issue a replacement |
| Revoke | Invalidate a single token (single-device logout) |
| Revoke all | Invalidate every token for a user (all-device logout) |
| Reuse detection | Detect stolen tokens — automatically kill all sessions |

---

## Why plaintext is never stored

The plaintext refresh token is generated once and returned to the client. **Only the SHA-256 hash is stored in the database.** This means:

- If the database is compromised, the attacker gets only hashes — useless without the plaintext
- SHA-256 is fast, which is fine here because refresh tokens are long random values (not passwords). Argon2 would be overkill and slow.

```
Client holds:   "dGVzdHRva2Vu..."          (base64url, 64 random bytes)
Database stores: sha256("dGVzdHRva2Vu...")   (64-char hex string)
```

---

## Token rotation

Each refresh token is **single-use**. When a client calls `/refresh`:

1. The old token is marked `revokedAt = now()`
2. A new token is issued in the same transaction
3. The old token's `replacedBy` field is set to the new token's ID (audit trail)

This means:
- If a token is stolen, the attacker and the real user will both try to use it
- Whoever uses it second will get a **reuse detection** response

---

## Reuse detection (token family invalidation)

If a **revoked** token is presented:

```
Revoked token presented
        │
        ▼  Query DB → token exists, revokedAt is set
        │
        ▼  log.warn("Refresh token reuse detected for userId=...")
        │
        ▼  refreshTokenRepository.revokeAllByUserId(userId, now)
              → all active sessions for this user are instantly killed
        │
        ▼  throw InvalidRefreshTokenException("Token reuse detected...")
```

The user must log in again. This is the correct response to a possible token theft scenario.

---

## Concurrency safety (pessimistic locking)

Multiple concurrent refresh calls with the same token (race condition) are prevented with a **pessimistic write lock**:

```java
@Lock(PESSIMISTIC_WRITE)
Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);
```

The database row is locked for the duration of the transaction. Only one of two simultaneous refresh calls for the same token will win — the other will see the token already revoked and trigger reuse detection.

---

## Key classes

| Class | Role |
|---|---|
| `RefreshTokenService` | Issue, rotate, revoke, reuse detection |
| `RefreshToken` | JPA entity — `userId`, `tokenHash`, `expiresAt`, `revokedAt`, `replacedBy` |
| `RefreshTokenRepository` | DB queries including `findByTokenHashForUpdate` with pessimistic lock |

---

## RefreshToken entity fields

| Field | Type | Description |
|---|---|---|
| `id` | UUID | Primary key |
| `userId` | UUID | Owner (not a FK — prevents cascade delete issues) |
| `tokenHash` | String | SHA-256 of the plaintext token |
| `expiresAt` | Instant | When the token becomes invalid |
| `revokedAt` | Instant | Set when token is used or detected as stolen |
| `replacedBy` | UUID | ID of the token that replaced this one (rotation audit trail) |

---

## Token lifetime

| Config | Env var | Default |
|---|---|---|
| Refresh token TTL | `JWT_REFRESH_TOKEN_EXPIRATION` | `P30D` (30 days) |
| Access token TTL | `JWT_ACCESS_TOKEN_EXPIRATION` | `PT15M` (15 minutes) |

Access tokens are **not** revocable (they're stateless JWTs). If an access token is compromised, it's valid until it expires. Design your access token TTL to be short enough that this risk is acceptable for your use case.

---

## What is NOT here

- JWT generation → `authentication/JwtService`
- The `/refresh` endpoint → `authentication/AuthController`
- Account locking after login failures → `authentication/AuthService`
