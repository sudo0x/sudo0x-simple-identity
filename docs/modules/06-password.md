# Module: Password

**Package:** `com.sudo0x.simple.identity.password`

Handles password management operations that go beyond simple login: changing your password while authenticated, resetting a forgotten password via email, and verifying your email address.

---

## What this module does

| Responsibility | Description |
|---|---|
| Change password | Authenticated user changes their own password |
| Forgot password | Unauthenticated user requests a reset link by email |
| Reset password | Complete the reset using a time-limited token from the email |
| Verify email | Confirm an email address using a token sent by email |

---

## Key classes

| Class | Role |
|---|---|
| `PasswordService` | Core logic for all four operations |
| `PasswordController` | REST endpoints (`/api/v1/auth/...`) |
| `EmailService` | Interface — sends emails (reset link, verification link) |
| `DevLoggingEmailService` | Dev implementation — logs the token to the console instead of sending email |
| `PasswordResetToken` | JPA entity — single-use time-limited reset token |
| `EmailVerificationToken` | JPA entity — single-use time-limited verification token |

---

## Change password flow

```
POST /api/v1/auth/change-password
  { "currentPassword": "...", "newPassword": "..." }
  Authorization: Bearer <token>
         │
         ▼  Verify currentPassword against stored Argon2id hash
              If wrong → InvalidCredentialsException
         │
         ▼  Validate newPassword against password policy
         │
         ▼  credentialService.updatePassword(userId, newPassword)
              → new Argon2id hash stored
         │
         ▼  refreshTokenService.revokeAllForUser(userId)
              → all other sessions invalidated (security hygiene)
         │
         ▼  Record PASSWORD_CHANGED audit event
         │
         ▼  Return 200
```

After a password change, all existing refresh tokens for the user are revoked. The client must log in again on other devices. This is intentional — if a password was changed because of a breach, you want all sessions killed.

---

## Forgot password flow

```
POST /api/v1/auth/forgot-password
  { "email": "alice@example.com" }
         │
         ▼  Look up user by email
              If not found → return 200 (same response — no user enumeration)
         │
         ▼  Generate secure random token (64 bytes → base64url)
              Store SHA-256 hash in password_reset_tokens table
              expiresAt = now + resetTokenExpiration (default 1 hour)
         │
         ▼  emailService.sendPasswordResetEmail(email, plaintext token)
              Dev: logs "Reset token: <token>" to console
              Prod: implement a real email sender
         │
         ▼  Return 200 (always — same response whether email exists or not)
```

**User enumeration prevention:** The response is always `200 OK` regardless of whether the email exists. An attacker cannot use the forgot-password endpoint to discover which emails are registered.

---

## Reset password flow

```
POST /api/v1/auth/reset-password
  { "token": "<token from email>", "newPassword": "..." }
         │
         ▼  Compute SHA-256 of submitted token
              Look up in DB
              If not found → InvalidRefreshTokenException (same error, no info leakage)
              If expired → TokenExpiredException
              If already used → InvalidRefreshTokenException
         │
         ▼  Validate newPassword against password policy
         │
         ▼  credentialService.updatePassword(userId, newPassword)
         │
         ▼  Mark token as used (usedAt = now())
         │
         ▼  revokeAllForUser → all sessions invalidated
         │
         ▼  Record PASSWORD_RESET audit event
         │
         ▼  Return 200
```

---

## Email verification flow

```
POST /api/v1/auth/verify-email
  { "token": "<token from email>" }
         │
         ▼  Hash lookup → validate token not expired / not used
         │
         ▼  user.emailVerified = true
         │
         ▼  Mark token used
         │
         ▼  Record EMAIL_VERIFIED audit event
         │
         ▼  Return 200
```

Email verification is **optional** (`identity.email.verification-required=false` by default). Enable it when you want to confirm users own the email they registered with.

---

## Token storage

Password reset and email verification tokens follow the same security pattern as refresh tokens:

| What | How |
|---|---|
| Plaintext token | Returned to user in the email only — never stored |
| Stored in DB | SHA-256 hash of plaintext |
| Expiry | Configured via `EMAIL_VERIFICATION_EXPIRY` / `PASSWORD_RESET_EXPIRY` |
| Single-use | `usedAt` is set when the token is consumed |

---

## Implementing a real email sender (production)

`EmailService` is an interface. The `DevLoggingEmailService` implementation logs tokens to the console. For production, create a new `@Service` that implements `EmailService` and sends real email:

```java
@Service
@Primary          // replaces DevLoggingEmailService
@Profile("prod")  // only active in production
public class SmtpEmailService implements EmailService {
    @Override
    public void sendPasswordResetEmail(String to, String token) {
        // send via JavaMailSender / SendGrid / SES / etc.
    }
    @Override
    public void sendVerificationEmail(String to, String token) { ... }
}
```

---

## Configuration

| Config | Env var | Default | Description |
|---|---|---|---|
| `email.verification-required` | `EMAIL_VERIFICATION_REQUIRED` | `false` | Block login until email verified |
| `email.verification-token-expiration` | `EMAIL_VERIFICATION_EXPIRY` | `PT24H` | Verification link TTL |
| `email.reset-token-expiration` | `PASSWORD_RESET_EXPIRY` | `PT1H` | Reset link TTL |
