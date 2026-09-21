# Identity Service — Module Documentation Index

This folder contains one markdown file per major feature module. Use it when you need to understand what a module does, why it was designed a certain way, or how its pieces fit together.

---

## Modules

| # | File | Module | One-line summary |
|---|---|---|---|
| 1 | [01-authentication.md](./01-authentication.md) | Authentication | Login, refresh, logout, /me, JWKS |
| 2 | [02-credential.md](./02-credential.md) | Credential | Argon2id password hashing, policy validation |
| 3 | [03-session.md](./03-session.md) | Session | Refresh token rotation and reuse detection |
| 4 | [04-user.md](./04-user.md) | User | User CRUD, soft-delete, role assignment |
| 5 | [05-role-permission.md](./05-role-permission.md) | Role & Permission | RBAC — roles, permissions, how they flow into JWTs |
| 6 | [06-password.md](./06-password.md) | Password | Change password, forgot/reset, email verification |
| 7 | [07-audit.md](./07-audit.md) | Audit | Async security event log |
| 8 | [08-common.md](./08-common.md) | Common | Config, security filter, response envelope, exception handler |

---

## Quick orientation

```
com.sudo0x.simple.identity
│
├── common/         → 08-common.md
│   ├── config/     AppProperties, SecurityConfig, SeedDataInitializer
│   ├── security/   JwtAuthenticationFilter, SecurityPrincipal, @CurrentUser
│   ├── exception/  All exceptions + GlobalExceptionHandler
│   ├── response/   ApiResponse, PagedResponse
│   ├── web/        RequestIdFilter, CookieUtil
│   └── audit/      BaseEntity (createdAt/updatedAt)
│
├── authentication/ → 01-authentication.md
│   AuthService, JwtService, AuthController, JwksController, DTOs
│
├── credential/     → 02-credential.md
│   CredentialService, Credential entity
│
├── session/        → 03-session.md
│   RefreshTokenService, RefreshToken entity
│
├── user/           → 04-user.md
│   UserService, UserController, User entity, UserMapper
│
├── role/           → 05-role-permission.md
│   RoleService, RoleController, Role entity
│
├── permission/     → 05-role-permission.md
│   PermissionService, PermissionController, Permission entity
│
├── password/       → 06-password.md
│   PasswordService, PasswordController, EmailService
│
└── audit/          → 07-audit.md
    AuditService, AuditEvent entity, AuditEventType enum
```

---

## Key design decisions (quick reference)

| Decision | Why |
|---|---|
| Feature-oriented packages | All code for one feature lives together; easy to navigate |
| Credential in a separate table | Separates identity data from secret data |
| SHA-256 for token hashes (not Argon2) | Tokens are long random values, not passwords; speed is fine, brute-force is infeasible |
| Argon2id for passwords | OWASP-recommended; slow by design; protects against brute-force on compromised DB |
| RSA-2048 asymmetric JWT signing | Consuming services can verify JWTs without the private key |
| Pessimistic lock on refresh token rotation | Prevents race condition where two concurrent refreshes both succeed |
| Async audit writes | Audit failures never fail the user's request |
| No FK on audit.userId | Audit records outlive the user |
| Soft delete (DELETED status) | Preserves audit trail; user data not accidentally gone forever |
| Generic error messages on login | Prevents username enumeration attacks |
| Dummy password check for unknown users | Prevents timing attacks that reveal username existence |
| CSRF disabled + SameSite=Lax cookies | Safe combination for stateless APIs with optional cookie auth |
| `existingSecret` pattern in Helm | Production credentials are never stored in Helm values |
