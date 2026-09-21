# Module: Credential

**Package:** `com.sudo0x.simple.identity.credential`

Handles everything related to user passwords: storing them safely, updating them, and checking if a given plaintext password matches the stored hash.

---

## What this module does

| Responsibility | Description |
|---|---|
| Create credential | Hash a new password and save it linked to a user |
| Update credential | Re-hash and replace an existing password |
| Verify credential | Check a plaintext password against the stored hash |
| Policy validation | Enforce minimum length, uppercase, lowercase, digit, special char rules |

---

## Why a separate table?

Passwords are stored in a `credentials` table, not inside the `users` table. Reasons:

1. **Single responsibility** — `User` owns identity data; `Credential` owns secret data. Joining these in one entity makes every user query pull the hash unnecessarily.
2. **Future flexibility** — a user might one day have multiple credential types (password, passkey, OAuth). Keeping credentials separate supports this without changing the `User` entity.
3. **Audit isolation** — querying the audit log for user changes doesn't expose any credential columns by accident.

---

## Password storage: Argon2id

Passwords are hashed with **Argon2id** (the Spring Security 5.8+ default), which is the current OWASP recommendation for password hashing.

| Property | Value |
|---|---|
| Algorithm | Argon2id |
| Plaintext stored | Never |
| Hash stored | In `credentials.password_hash` |
| Timing constant | Argon2 is intentionally slow — brute-forcing is expensive |

The `PasswordEncoder` bean is created in `SecurityConfig`:
```java
Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()
```

---

## Password policy

Configurable via environment variables (or Helm `values.yaml`):

| Config | Env var | Default | Description |
|---|---|---|---|
| `minimumLength` | `PASSWORD_MIN_LENGTH` | `8` | Minimum character count |
| `requireUppercase` | `PASSWORD_REQUIRE_UPPERCASE` | `true` | At least one A-Z |
| `requireLowercase` | `PASSWORD_REQUIRE_LOWERCASE` | `true` | At least one a-z |
| `requireDigit` | `PASSWORD_REQUIRE_DIGIT` | `true` | At least one 0-9 |
| `requireSpecial` | `PASSWORD_REQUIRE_SPECIAL` | `false` | At least one `!@#$%^&*` |

Policy is checked in `CredentialService.validatePasswordPolicy()` before any hashing occurs. All violated rules are collected and returned together in a single `PasswordPolicyException` — the client sees every problem at once, not one at a time.

---

## Key classes

| Class | Role |
|---|---|
| `CredentialService` | Policy validation, hashing, verification |
| `Credential` | JPA entity — `userId` (FK) + `passwordHash` |
| `CredentialRepository` | `findByUserId(UUID)` |

---

## How verify() works

```java
credentialService.verify(userId, rawPassword)
```

1. Load `Credential` by `userId`
2. If not found → return `false` (no exception — caller decides what to do)
3. `passwordEncoder.matches(rawPassword, storedHash)` — Argon2 re-hashes and compares

The method intentionally returns `false` instead of throwing when credentials are missing, so the login service can treat "credential not found" the same as "wrong password" from the outside.

---

## What is NOT here

- Password reset tokens → `password` module
- Password change (authenticated) → `password` module
- The password policy config source → `common/config/AppProperties.PasswordProperties`
