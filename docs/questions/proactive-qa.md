# Things You Should Know — Proactive Q&A

These are questions you did not ask, but should know the answers to before working with or extending this project. Written proactively so that one year from now, the reasoning is still clear.

---

## 1. The access token cannot be revoked. Is that a security problem?

**Yes, it is a trade-off — and it is intentional.**

JWT access tokens are **stateless**. Once signed, the Identity Service has no record of them. There is no database lookup on every request. That is the entire point — consuming services validate tokens locally using the public key, with zero calls back to the Identity Service.

The consequence: **if an access token is stolen, it is valid until it expires.**

The mitigation is the **short expiry**:

```
Access token TTL: 15 minutes (default)
```

In 15 minutes, an attacker with a stolen access token loses it. Reduce the TTL if your threat model requires it. Common real-world values: 5–15 minutes.

**Refresh tokens ARE revocable** — they are stored in the database and validated on every use. Logging out revokes the refresh token immediately. The attacker cannot get a new access token after that.

```
Access token stolen
        │
        ├── attacker can use it for up to 15 minutes
        │
        └── after expiry → useless
              (attacker cannot refresh because refresh token is revoked)
```

**If you need instant revocation of access tokens**, you would need a token denylist (e.g., a Redis set of revoked JWT IDs). This adds latency to every request and couples all services to the denylist store. Most systems accept the short-TTL trade-off instead.

---

## 2. Why RSA asymmetric signing instead of HMAC (HS256)?

**Because HMAC requires sharing a secret key with every service that validates tokens.**

With HMAC (symmetric):
```
Identity Service signs with key K
Consuming Service validates with the same key K
→ Every consuming service must know K
→ K must be securely distributed to every service
→ Any service that knows K can also forge tokens
```

With RSA (asymmetric):
```
Identity Service signs with private key (secret, never shared)
Consuming Service validates with public key (safe to share, no risk)
→ A compromised consuming service cannot forge tokens
→ Public key can be fetched openly from /.well-known/jwks.json
```

In a microservices architecture with many consuming services, HMAC is a key distribution nightmare. RSA keeps the signing authority in one place.

---

## 3. What happens when the JWT private key is rotated?

**All existing access tokens immediately become invalid.** This is important to plan for.

When you replace `private.pem` and restart the service:
- The new public key is served at `/.well-known/jwks.json`
- Consuming services that cache the old JWKS will reject all tokens signed with the new key until their cache refreshes (usually within minutes)
- Consuming services that cache the new JWKS will reject all tokens signed with the old key (all currently active sessions)

```
Key rotation moment
        │
        ├── active access tokens (old key) → immediately invalid
        ├── active refresh tokens → still valid (stored in DB, key-independent)
        │
        └── users must: discard old access token → call /refresh → get new access token
```

**Practical rotation procedure:**
1. Generate new key pair
2. Update the Kubernetes Secret with new keys
3. Do a rolling restart (`kubectl rollout restart`)
4. Clients that get a 401 should automatically call `/refresh` to get a new access token — this is standard resilient client behavior

The refresh token mechanism means users do not have to log in again — they just need a client that automatically refreshes on 401.

---

## 4. Should consuming microservices call the Identity Service on every request?

**No. Never.**

This is a common misunderstanding. The Identity Service's job is to **issue** tokens, not to **validate** them on every request.

```
WRONG:
Client → Consuming Service → Identity Service (validate token?) → response
                                     ↑ every single request, adds latency, single point of failure

CORRECT:
Client → Consuming Service → validates JWT locally using public key → response
         (zero calls to Identity Service per request)
```

JWT validation is a local cryptographic operation: verify the RSA signature against the public key, check the expiry, read the claims. It takes microseconds and requires no network call.

The consuming service fetches the public key **once** from `/.well-known/jwks.json` and caches it. Spring Security's `oauth2-resource-server` does this automatically.

The Identity Service only needs to be reachable when:
- A user is logging in
- A user is refreshing their token
- A user is logging out

---

## 5. Why Flyway instead of `spring.jpa.hibernate.ddl-auto: update`?

`ddl-auto: update` is convenient for early development. It is dangerous in production for these reasons:

| Concern | `ddl-auto: update` | Flyway |
|---|---|---|
| Dropping columns | Will NOT drop unused columns (data loss risk) | You control exactly what happens |
| Renaming columns | Creates a new column, old one stays (data divergence) | Explicit `ALTER TABLE RENAME COLUMN` |
| Adding constraints | May fail silently on existing data | Fails loudly at migration time |
| Production visibility | Schema changes are invisible — no audit trail | Every change is a versioned, reviewable SQL file |
| Rollback | No mechanism | `helm rollback` + compensating migration |
| Team collaboration | Two developers may get different schemas | Everyone runs the same migrations in order |

`ddl-auto: validate` (what this project uses) is the safe production setting — Hibernate checks that the schema matches the entities on startup and **refuses to start** if they don't match. This catches missed migrations before the application accepts traffic.

---

## 6. Why Testcontainers instead of H2 (in-memory database) for tests?

H2 is not PostgreSQL. Tests that pass on H2 can fail in production on PostgreSQL.

Real differences that matter:

| Feature | H2 | PostgreSQL |
|---|---|---|
| `UUID` primary key generation | Different behavior | Native `gen_random_uuid()` |
| `ENUM` types | Simulated | Native PostgreSQL enum |
| JSON columns | Limited support | Full `jsonb` operators |
| Window functions | Partial | Full support |
| `ILIKE` (case-insensitive LIKE) | Not supported | Native |
| Index types | Limited | GIN, GiST, BRIN, etc. |
| Flyway SQL dialect | May accept invalid PostgreSQL SQL | Exact match |

Testcontainers starts a **real PostgreSQL Docker container** for each test run. Tests run against the actual database they will use in production. If a migration or query works in tests, it will work in production.

The trade-off: tests require Docker to be running. This is standard in any CI/CD pipeline (GitHub Actions, GitLab CI, Jenkins all support Docker).

---

## 7. Why UUID primary keys instead of auto-increment integers?

**Security, distribution, and privacy.**

| Concern | Auto-increment (`1, 2, 3…`) | UUID |
|---|---|---|
| Sequential guessing | `GET /users/4` — easy to enumerate all users | `GET /users/550e8400-e29b-41d4-a716-446655440000` — not guessable |
| Data volume leakage | ID `10000` reveals ~10,000 users exist | UUID reveals nothing |
| Distributed generation | Conflicts when merging datasets from multiple sources | Globally unique |
| Merge / import | IDs may collide when importing data | No collisions |

For a user management / identity system specifically, exposing sequential integer user IDs is a real information disclosure risk. An attacker can enumerate `GET /users/1` through `GET /users/N` to discover every user in the system. UUIDs eliminate this.

---

## 8. Expired tokens accumulate in the database. Is that a problem?

**Yes, eventually — and a cleanup job should be added for production.**

Every login creates a refresh token row. After 30 days it expires. After 60 days there are 60 days of expired rows sitting in `refresh_tokens`. On a high-volume system this becomes a large table.

The missing piece is a **scheduled cleanup job**:

```java
@Scheduled(cron = "0 0 3 * * *")  // 3am daily
@Transactional
public void deleteExpiredTokens() {
    refreshTokenRepository.deleteByExpiresAtBefore(Instant.now().minus(7, DAYS));
    // Keep 7 days past expiry for audit purposes, then delete
}
```

The same applies to `password_reset_tokens` and `email_verification_tokens`.

Without this, the tables grow indefinitely. Add `@EnableScheduling` to `IdentityApplication` and a `@Scheduled` cleanup method when you move to production with real user volume.

---

## 9. Why is the `credential` table separate from the `users` table?

**Single responsibility and future flexibility.**

The `users` table owns identity data: who the user is.
The `credentials` table owns secret data: how the user proves who they are.

Keeping them separate means:
- Every `SELECT * FROM users` never accidentally exposes a password hash
- The credential mechanism can be replaced or extended without touching the `User` entity (e.g., adding passkey support, OAuth provider linking, or multi-factor authentication later)
- The `User` entity stays clean and focused

This is the same pattern used by major identity systems. Okta, for example, stores credential data in a completely separate, more hardened store than user profile data.

---

## 10. What is `ddl-auto: validate` and why does it protect you?

On startup, Hibernate reads every `@Entity` class and compares it against the actual database schema. If anything does not match — a missing column, a wrong type, a missing table — **the application refuses to start**.

```
Application starts
        │
        ▼  Flyway runs pending migrations
        │
        ▼  Hibernate validates schema against entities
        │
        ├── Schema matches entities → startup continues, application accepts traffic
        │
        └── Schema does not match → startup fails, Kubernetes pod never becomes Ready
              readinessProbe fails → no traffic is routed to this pod
              → your old pods keep running (rolling update safety net)
```

This means a failed migration or a forgotten entity change is caught **before** the broken pod ever serves a single request. Combined with rolling updates (`maxUnavailable: 0`), a bad deployment will fail silently behind the scenes while the old version keeps running — zero downtime, no bad requests served.

---

## 11. Why is there no `ROLE_SUPERADMIN` or root user that cannot be deleted?

**Because hard-coded privilege levels are brittle and hard to audit.**

This project uses database-backed RBAC. Any user can be assigned any role. There is no special hard-coded user. This is intentional:

- An `ADMIN` user is just a `USER` with the `ADMIN` role assigned
- Anyone with `user:delete` permission can delete an admin
- Anyone with `role:update` permission can remove the `ADMIN` role

**Real-world implication:** in production, ensure at least two admin users exist and that `user:delete` permission is guarded carefully. Consider removing `user:delete` from the default `ADMIN` role and creating a separate `SUPERADMIN` role with only one or two trusted users.

The seed data (`V10` migration + `SeedDataInitializer`) creates the first admin for bootstrapping. After that, role and permission management is entirely through the API.
