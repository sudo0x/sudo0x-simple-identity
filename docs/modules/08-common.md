# Module: Common

**Package:** `com.sudo0x.simple.identity.common`

Shared infrastructure used by every other module: configuration, security filter chain, HTTP response envelopes, exception handling, and web utilities. Nothing here is specific to a single feature.

---

## Sub-packages

| Sub-package | What's in it |
|---|---|
| `common/config` | Spring configuration beans — security, OpenAPI, app properties, seed data |
| `common/security` | JWT filter, `SecurityPrincipal`, `@CurrentUser` annotation |
| `common/exception` | All custom exceptions + global exception handler |
| `common/response` | Standard API response wrapper (`ApiResponse`, `PagedResponse`) |
| `common/web` | `RequestIdFilter`, `CookieUtil` |
| `common/audit` | `BaseEntity` (createdAt / updatedAt auditing) |

---

## AppProperties

**File:** `common/config/AppProperties.java`

All application configuration is bound from environment variables (or `application.yml`) into a single typed record using `@ConfigurationProperties(prefix = "identity")`. This means:

- **No scattered `@Value` annotations** — all config is in one place
- **Compile-time safety** — misspelled property names are caught at startup
- **Testable** — unit tests construct `AppProperties` directly

```java
AppProperties props = new AppProperties(
    new JwtProperties("identity-service", Duration.ofMinutes(15), ...),
    new SecurityProperties(5, Duration.ofMinutes(15), new CorsProperties(...)),
    new PasswordProperties(8, true, true, true, false),
    new EmailProperties(Duration.ofHours(24), Duration.ofHours(1), false),
    new SeedProperties(false, "admin", "admin@localhost", ""),
    new CookieProperties(false, true, true, "Lax", "", "/", "access_token", "refresh_token")
);
```

Nested records:

| Record | Prefix | Description |
|---|---|---|
| `JwtProperties` | `identity.jwt.*` | Issuer, token TTLs, key locations |
| `SecurityProperties` | `identity.security.*` | Login attempts, lock duration, CORS |
| `PasswordProperties` | `identity.password.*` | Policy rules |
| `EmailProperties` | `identity.email.*` | Token TTLs, verification requirement |
| `SeedProperties` | `identity.seed.*` | Admin seed user config |
| `CookieProperties` | `identity.cookie.*` | Cookie auth settings |

---

## SecurityConfig

**File:** `common/config/SecurityConfig.java`

Configures the Spring Security filter chain:

- **Session policy:** `STATELESS` — no HTTP sessions, no cookies managed by Spring
- **CSRF:** Disabled — safe for bearer-token APIs; when cookie auth is on, SameSite=Lax mitigates CSRF
- **CORS:** Configured from `AppProperties.SecurityProperties.CorsProperties`; `allowCredentials` is `true` only when `cookie.enabled=true`
- **Password encoder:** `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`
- **Public endpoints:** login, refresh, forgot-password, reset-password, verify-email, JWKS, Actuator health
- **Everything else:** requires a valid JWT
- **Custom 401/403:** Returns `ApiResponse.error(...)` JSON instead of Spring's default HTML error pages

---

## JwtAuthenticationFilter

**File:** `common/security/JwtAuthenticationFilter.java`

Runs on every request. Extracts and validates the JWT, then populates `SecurityContextHolder` if valid.

Token extraction order:
1. `Authorization: Bearer <token>` header
2. `access_token` HttpOnly cookie (only when `cookie.enabled=true`)

If the token is valid:
- Roles are registered as `ROLE_ADMIN`, `ROLE_USER`, etc.
- Permissions are registered as `permission:user:read`, `permission:role:create`, etc.

If the token is missing or invalid: filter continues without authentication — the endpoint's authorization rules decide what happens next.

---

## SecurityPrincipal and @CurrentUser

**Files:** `common/security/SecurityPrincipal.java`, `common/security/CurrentUser.java`

`SecurityPrincipal` is a plain record stored as the authentication principal in `SecurityContextHolder`:

```java
public record SecurityPrincipal(UUID userId, String username, Set<String> roles, Set<String> permissions) {}
```

`@CurrentUser` is a meta-annotation that injects the principal directly into a controller method parameter:

```java
@GetMapping("/me")
public ResponseEntity<...> me(@CurrentUser SecurityPrincipal principal) {
    // principal.userId(), principal.username(), principal.roles(), etc.
}
```

This avoids boilerplate like `(SecurityPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal()` everywhere.

---

## API response envelope

**Files:** `common/response/ApiResponse.java`, `PagedResponse.java`, `FieldError.java`

Every endpoint returns a consistent JSON structure:

```json
{
  "success": true,
  "data": { ... },
  "message": null,
  "requestId": "a1b2c3d4",
  "timestamp": "2026-01-01T00:00:00Z"
}
```

For errors:
```json
{
  "success": false,
  "message": "Validation failed",
  "errors": [
    { "field": "password", "code": "NotBlank", "message": "Password is required" }
  ],
  "requestId": "a1b2c3d4",
  "timestamp": "2026-01-01T00:00:00Z"
}
```

Static factory methods:
- `ApiResponse.ok(data)` — 200 with payload
- `ApiResponse.ok(null, "Logged out successfully")` — 200 with message only
- `ApiResponse.error("Something went wrong")` — error without field details
- `ApiResponse.error("Validation failed", errors)` — error with field-level details

---

## GlobalExceptionHandler

**File:** `common/exception/GlobalExceptionHandler.java`

`@RestControllerAdvice` that catches every exception thrown from controllers and services and maps it to the correct HTTP status + `ApiResponse` body. Clients always get a consistent JSON structure regardless of what went wrong.

| Exception | HTTP status |
|---|---|
| `MethodArgumentNotValidException` | 400 — includes field-level errors |
| `PasswordPolicyException` | 400 |
| `UsernameAlreadyExistsException` | 409 |
| `EmailAlreadyExistsException` | 409 |
| `InvalidCredentialsException` | 401 — generic message (no user enumeration) |
| `AccountLockedException` | 401 |
| `AccountDisabledException` | 401 — generic message |
| `InvalidRefreshTokenException` | 401 |
| `TokenExpiredException` | 401 |
| `UserNotFoundException` | 404 |
| `RoleNotFoundException` | 404 |
| `PermissionNotFoundException` | 404 |
| `AccessDeniedException` | 403 |
| `Exception` (fallback) | 500 — logs the full stack trace |

---

## RequestIdFilter

**File:** `common/web/RequestIdFilter.java`

Runs on every request before any other filter. Reads `X-Request-ID` from the incoming request header (or generates a UUID if absent), stores it in MDC (`requestId`) and as a request attribute. Every log line produced during the request includes `requestId`, making it easy to trace a single request through all log output.

---

## CookieUtil

**File:** `common/web/CookieUtil.java`

Helper for setting and clearing HttpOnly cookies. Used by `AuthController` when `cookie.enabled=true`.

| Method | Description |
|---|---|
| `setAccessTokenCookie(response, token)` | Sets `access_token` cookie with JWT TTL as `Max-Age` |
| `setRefreshTokenCookie(response, token)` | Sets `refresh_token` cookie with refresh TTL as `Max-Age` |
| `clearAuthCookies(response)` | Sets both cookies with `Max-Age=0` (browser deletes them) |

Cookies are always `HttpOnly=true`, and `Secure` / `SameSite` / `Domain` / `Path` are driven by `AppProperties.CookieProperties`.

---

## SeedDataInitializer

**File:** `common/config/SeedDataInitializer.java`

Runs once at startup when `identity.seed.enabled=true`. Creates a default `admin` user with the `ADMIN` role if one doesn't already exist. This is how you get an initial user to log in during development.

**Never enable in production.** The `SEED_ENABLED=false` default ensures this never runs accidentally.

---

## BaseEntity

**File:** `common/audit/BaseEntity.java`

`@MappedSuperclass` extended by all JPA entities. Provides:

| Field | Populated by |
|---|---|
| `id` (UUID) | `@GeneratedValue(strategy = UUID)` on first save |
| `createdAt` (Instant) | `@CreatedDate` — Spring Data auditing |
| `updatedAt` (Instant) | `@LastModifiedDate` — Spring Data auditing |

`@EnableJpaAuditing` on `IdentityApplication` activates this. All entities get audit timestamps automatically without any code in the entity itself.
