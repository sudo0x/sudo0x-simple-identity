# Identity Service

A production-grade, standalone Identity Service built with Java 21 and Spring Boot 3.x.
Designed as a reusable authentication and authorization microservice that any Spring Boot application can integrate with.

---

## Table of Contents

1. [What Is This?](#what-is-this)
2. [Architecture](#architecture)
3. [Technology Stack](#technology-stack)
4. [Domain Model](#domain-model)
5. [Authentication Flow](#authentication-flow)
6. [JWT Architecture](#jwt-architecture)
7. [Refresh Token Architecture](#refresh-token-architecture)
8. [API Documentation](#api-documentation)
9. [Local Development](#local-development)
10. [Database Setup](#database-setup)
11. [Environment Variables](#environment-variables)
12. [Docker Usage](#docker-usage)
13. [Running Tests](#running-tests)
14. [Production Deployment](#production-deployment)
15. [Security Considerations](#security-considerations)
16. [JWT Validation in Consuming Services](#jwt-validation-in-consuming-services)
17. [How to Obtain the Public Key](#how-to-obtain-the-public-key)
18. [Integrating a Future Spring Boot Application](#integrating-a-future-spring-boot-application)

---

## What Is This?

The Identity Service is a self-contained microservice responsible for:

- **User management** — registration, CRUD, status management
- **Authentication** — username/password login, JWT access tokens, refresh tokens
- **Authorization** — Role-Based Access Control (RBAC) with database-backed roles and permissions
- **Session management** — secure refresh token rotation with reuse detection
- **Password management** — hashing (Argon2id), change, forgot/reset flows
- **Email verification** — configurable email verification flow
- **Security audit** — event log for logins, password changes, role assignments, etc.

Each application deployment gets its own instance of the Identity Service with its own database. Users are not shared across applications.

---

## Architecture

```
com.sudo0x.simple.identity
├── common/            # Shared: config, exceptions, response, security, web
├── user/              # User entity, CRUD, mapper, repository, service, controller
├── role/              # Role entity, CRUD
├── permission/        # Permission entity, listing
├── authentication/    # Login, refresh, logout, /me, JWKS
├── credential/        # Password hashing and verification (separate from User)
├── session/           # Refresh token management
├── password/          # Change/forgot/reset password, email verification
└── audit/             # Security audit event log
```

Feature-oriented packaging keeps related code co-located. Each domain has clear internal boundaries.

---

## Technology Stack

| Layer           | Technology                                |
|-----------------|-------------------------------------------|
| Runtime         | Java 21 (LTS)                             |
| Framework       | Spring Boot 3.4.x                         |
| Security        | Spring Security 6.x                       |
| Persistence     | Spring Data JPA + Hibernate               |
| Database        | PostgreSQL 16                             |
| Migrations      | Flyway                                    |
| JWT             | JJWT 0.12.x (RSA-256 asymmetric signing)  |
| Password hashing| Argon2id (via Spring Security Crypto)     |
| Build           | Maven                                     |
| Testing         | JUnit 5, Mockito, Testcontainers          |
| Documentation   | springdoc-openapi (Swagger UI)            |
| Observability   | Spring Boot Actuator                      |
| Containerization| Docker + Docker Compose                   |

---

## Domain Model

```
User
 ├── id (UUID)
 ├── username (unique)
 ├── email (unique)
 ├── status (ACTIVE | INACTIVE | LOCKED | DELETED)
 ├── emailVerified
 ├── failedLoginAttempts
 ├── lockedUntil
 │
 ├── Credential (separate table)
 │     └── passwordHash (Argon2id)
 │
 ├── Roles (many-to-many)
 │     └── Role
 │           ├── name (unique)
 │           └── Permissions (many-to-many)
 │                 └── Permission
 │                       └── code (e.g. "user:read", "role:create")
 │
 └── RefreshTokens
       ├── tokenHash (SHA-256 of plaintext)
       ├── expiresAt
       └── revokedAt
```

Passwords and refresh tokens are **never stored in plaintext**. Only cryptographic hashes are persisted.

---

## Authentication Flow

```
POST /api/v1/auth/login
        │
        ▼ find user by username
        │
        ▼ check account status (ACTIVE required)
        │
        ▼ verify Argon2id password hash
        │
        ▼ reset failed attempt counter
        │
        ▼ load roles + permissions (single JOIN FETCH query)
        │
        ▼ generate RSA-signed JWT access token (15 min default)
        │
        ▼ generate secure random refresh token
        │   store SHA-256 hash in refresh_tokens table
        │
        ▼ return { accessToken, refreshToken, tokenType, expiresIn }
```

Authentication failures use a **generic error message** to prevent user enumeration.

Brute-force protection: after N failed attempts (configurable, default 5), the account is temporarily locked for a configurable duration (default 15 minutes).

---

## JWT Architecture

The Identity Service signs JWTs with an **RSA-2048 private key**.

Consuming services verify JWTs using the corresponding **public key only** — they never need the private key.

**JWT payload example:**
```json
{
  "iss": "identity-service",
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "username": "john",
  "roles": ["ADMIN"],
  "permissions": ["user:read", "user:create", "role:read"],
  "iat": 1700000000,
  "exp": 1700000900
}
```

Access tokens are **short-lived** (default 15 minutes). They are not revocable centrally — design your system so that access token lifetime is acceptable for your security requirements.

### Key Management

**Local development:** Set `identity.jwt.private-key-location=generate` (default). An ephemeral RSA key pair is generated on startup. Suitable for development only.

**Production:** Generate a persistent RSA-2048 key pair:
```bash
# Generate private key
openssl genrsa -out private.pem 2048
# Extract public key
openssl rsa -in private.pem -pubout -out public.pem
```
Mount the files into the container and set:
```
JWT_PRIVATE_KEY_LOCATION=file:/run/secrets/private.pem
JWT_PUBLIC_KEY_LOCATION=file:/run/secrets/public.pem
```

---

## Refresh Token Architecture

```
Login
  │ issue plaintext refresh token (random 48 bytes, base64url-encoded)
  │ store SHA-256(token) in refresh_tokens table
  └──► return plaintext to client

Refresh (POST /api/v1/auth/refresh)
  │ receive plaintext token from client
  │ compute SHA-256, look up in DB with pessimistic lock
  │ check: not revoked, not expired
  │   ├── if REVOKED → reuse detected → revoke ALL sessions for this user
  │   └── if VALID   → mark old token revoked, issue new token
  └──► return new { accessToken, refreshToken }
```

**Token rotation** ensures each refresh token can only be used once. **Reuse detection** invalidates all sessions if a previously used token is presented again, protecting against token theft.

---

## API Documentation

When running locally, Swagger UI is available at:

```
http://localhost:8080/swagger-ui.html
```

### Endpoint Summary

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/auth/login` | Public | Login |
| POST | `/api/v1/auth/refresh` | Public | Refresh tokens |
| POST | `/api/v1/auth/logout` | Authenticated | Revoke current refresh token |
| POST | `/api/v1/auth/logout-all` | Authenticated | Revoke all refresh tokens |
| GET  | `/api/v1/auth/me` | Authenticated | Current user info |
| POST | `/api/v1/auth/change-password` | Authenticated | Change password |
| POST | `/api/v1/auth/forgot-password` | Public | Request password reset |
| POST | `/api/v1/auth/reset-password` | Public | Complete password reset |
| POST | `/api/v1/auth/verify-email` | Public | Verify email address |
| GET  | `/.well-known/jwks.json` | Public | RSA public key (JWKS) |
| GET  | `/api/v1/users` | `user:read` | List users |
| GET  | `/api/v1/users/{id}` | `user:read` | Get user |
| POST | `/api/v1/users` | `user:create` | Create user |
| PATCH| `/api/v1/users/{id}` | `user:update` | Update user |
| DELETE | `/api/v1/users/{id}` | `user:delete` | Delete user (soft) |
| PUT  | `/api/v1/users/{userId}/roles/{roleId}` | `role:update` | Assign role |
| DELETE | `/api/v1/users/{userId}/roles/{roleId}` | `role:update` | Remove role |
| GET  | `/api/v1/roles` | `role:read` | List roles |
| GET  | `/api/v1/roles/{id}` | `role:read` | Get role |
| POST | `/api/v1/roles` | `role:create` | Create role |
| PATCH| `/api/v1/roles/{id}` | `role:update` | Update role |
| DELETE | `/api/v1/roles/{id}` | `role:delete` | Delete role |
| PUT  | `/api/v1/roles/{roleId}/permissions/{permissionId}` | `role:update` | Add permission to role |
| DELETE | `/api/v1/roles/{roleId}/permissions/{permissionId}` | `role:update` | Remove permission from role |
| GET  | `/api/v1/permissions` | `permission:read` | List permissions |

---

## Local Development

### Prerequisites

- Java 21
- Maven 3.9+
- Docker + Docker Compose (for the database)
- (Optional) PostgreSQL client

### Quick Start

```bash
# 1. Start just the database
docker compose up postgres -d

# 2. Set admin seed password
export SEED_ADMIN_PASSWORD=Admin@1234!

# 3. Run with local profile (auto-seeds admin user, ephemeral JWT keys)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# 4. Open Swagger UI
open http://localhost:8080/swagger-ui.html
```

### Sample Login Request

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Admin@1234!"}'
```

Response:
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
    "refreshToken": "dGVzdC10b2tlbg...",
    "tokenType": "Bearer",
    "expiresIn": 900
  },
  "timestamp": "2026-01-01T00:00:00Z"
}
```

---

## Database Setup

Flyway manages all schema migrations. Tables are created automatically on first startup.

**Migration files** (`src/main/resources/db/migration/`):
- `V1` — users table
- `V2` — credentials table
- `V3` — roles and permissions tables
- `V4` — user_roles join table
- `V5` — role_permissions join table
- `V6` — refresh_tokens table
- `V7` — password_reset_tokens table
- `V8` — email_verification_tokens table
- `V9` — audit_events table
- `V10` — seed roles and permissions (ADMIN, MANAGER, USER + permissions)

**Manual setup** (if not using Docker):
```sql
CREATE DATABASE identity;
CREATE USER identity WITH PASSWORD 'identity';
GRANT ALL PRIVILEGES ON DATABASE identity TO identity;
```

---

## Environment Variables

See [`.env.example`](./.env.example) for a complete list with descriptions.

| Variable | Default | Description |
|----------|---------|-------------|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/identity` | PostgreSQL JDBC URL |
| `DATABASE_USER` | `identity` | Database username |
| `DATABASE_PASSWORD` | `identity` | Database password |
| `JWT_PRIVATE_KEY_LOCATION` | `generate` | Path to RSA private key PEM (`generate` for dev) |
| `JWT_PUBLIC_KEY_LOCATION` | `generate` | Path to RSA public key PEM |
| `JWT_ISSUER` | `identity-service` | JWT issuer claim |
| `JWT_ACCESS_TOKEN_EXPIRATION` | `PT15M` | Access token TTL (ISO-8601 duration) |
| `JWT_REFRESH_TOKEN_EXPIRATION` | `P30D` | Refresh token TTL |
| `MAX_LOGIN_ATTEMPTS` | `5` | Attempts before lockout |
| `ACCOUNT_LOCK_DURATION` | `PT15M` | Lock duration after max attempts |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated allowed origins |
| `SEED_ENABLED` | `false` | Enable admin seed user creation |
| `SEED_ADMIN_PASSWORD` | _(required if SEED_ENABLED=true)_ | Initial admin password |

---

## Docker Usage

### Start everything (app + database):

```bash
docker compose up -d
```

### Start with MailHog for email testing:

```bash
docker compose --profile mail up -d
# MailHog UI: http://localhost:8025
```

### Production build only:

```bash
docker build -t identity-service:latest .
docker run -p 8080:8080 \
  -e DATABASE_URL=jdbc:postgresql://host:5432/identity \
  -e DATABASE_USER=identity \
  -e DATABASE_PASSWORD=secret \
  -e JWT_PRIVATE_KEY_LOCATION=file:/run/secrets/private.pem \
  -e JWT_PUBLIC_KEY_LOCATION=file:/run/secrets/public.pem \
  -e SPRING_PROFILES_ACTIVE=prod \
  identity-service:latest
```

---

## Running Tests

```bash
# All tests (requires Docker for Testcontainers)
./mvnw test

# Skip integration tests (unit tests only)
./mvnw test -Dtest='!*IntegrationTest'

# Run a specific test class
./mvnw test -Dtest=JwtServiceTest
```

Tests use **Testcontainers** to spin up a real PostgreSQL instance — no H2 in-memory workarounds.

---

## Production Deployment

### Checklist

- [ ] `SPRING_PROFILES_ACTIVE=prod`
- [ ] `SEED_ENABLED=false`
- [ ] Real RSA-2048 key pair mounted securely (not committed to git)
- [ ] `JWT_PRIVATE_KEY_LOCATION` and `JWT_PUBLIC_KEY_LOCATION` point to mounted keys
- [ ] Strong `DATABASE_PASSWORD`
- [ ] `CORS_ALLOWED_ORIGINS` set to actual production origins
- [ ] Swagger UI disabled (automatic in prod profile)
- [ ] Actuator endpoints restricted (only `/health` and `/metrics` public)
- [ ] Non-root Docker user (included in Dockerfile)
- [ ] Graceful shutdown configured (`server.shutdown=graceful`)

### Generating RSA Keys for Production

```bash
# Private key (keep secret, never commit)
openssl genrsa -out private.pem 2048

# Public key (safe to distribute to consuming services)
openssl rsa -in private.pem -pubout -out public.pem

# Verify
openssl rsa -in private.pem -check -noout
```

---

## Security Considerations

| Concern | Implementation |
|---------|----------------|
| Password storage | Argon2id hashing (Spring Security defaults) |
| Refresh tokens | SHA-256 hash stored, plaintext only returned once |
| Password reset tokens | SHA-256 hash stored, single-use, time-limited |
| JWT signing | RSA-2048 asymmetric (private key never leaves Identity Service) |
| User enumeration | Generic error messages for login and password reset |
| Brute force | Configurable lockout after N failed attempts |
| Token reuse | Refresh token rotation + family invalidation on reuse |
| CSRF | Disabled (stateless bearer-token API — CSRF does not apply) |
| Secret logging | Passwords, tokens, keys are never logged |
| Soft deletes | Users are DELETED status, not physically removed (audit trail preserved) |

---

## JWT Validation in Consuming Services

Consuming services **do not call the Identity Service on every request**. They validate JWTs locally using the public key.

### Spring Boot Resource Server Setup

Add to `pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

`application.yml`:
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          # Point to the Identity Service JWKS endpoint
          jwk-set-uri: http://identity-service:8080/.well-known/jwks.json
```

`SecurityConfig.java` in the consuming service:
```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(authz -> authz
            .requestMatchers("/public/**").permitAll()
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
        );
    return http.build();
}

@Bean
public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter converter = new JwtGrantedAuthoritiesConverter();
    // Map JWT claims to Spring Security authorities
    // roles → ROLE_ADMIN, ROLE_USER, etc.
    // permissions → permission:user:read, etc.
    converter.setAuthoritiesClaimName("permissions");
    converter.setAuthorityPrefix("permission:");

    JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
    jwtConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
        List<GrantedAuthority> authorities = new ArrayList<>();
        // Add roles as ROLE_ authorities
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null) {
            roles.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
        }
        // Add permissions
        List<String> permissions = jwt.getClaimAsStringList("permissions");
        if (permissions != null) {
            permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority("permission:" + p)));
        }
        return authorities;
    });
    return jwtConverter;
}
```

Protect endpoints:
```java
.requestMatchers("/api/orders").hasAuthority("ROLE_USER")
.requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
```

---

## How to Obtain the Public Key

The public key is available via the JWKS endpoint (no authentication required):

```bash
curl http://identity-service:8080/.well-known/jwks.json
```

Response:
```json
{
  "keys": [{
    "kty": "RSA",
    "use": "sig",
    "alg": "RS256",
    "n": "...",
    "e": "AQAB"
  }]
}
```

Spring's `oauth2-resource-server` fetches and caches this automatically. You can also download the public key manually:

```bash
# Download PEM (if you have the public.pem file)
openssl rsa -in private.pem -pubout -out public.pem

# Configure consuming service to use the file instead
spring.security.oauth2.resourceserver.jwt.public-key-location=classpath:public.pem
```

---

## Integrating a Future Spring Boot Application

```
inventory-service
      │
      │  Authorization: Bearer <JWT>
      ▼
Spring Security Resource Server
      │
      ▼  validate signature using Identity Service public key
      │  (no call to Identity Service per request)
      ▼
Extract: userId (sub), username, roles, permissions from JWT claims
      │
      ▼
Apply @PreAuthorize / hasAuthority() guards
```

### Integration Steps

1. Add `spring-boot-starter-oauth2-resource-server` dependency
2. Configure `jwk-set-uri` pointing to Identity Service
3. Implement `JwtAuthenticationConverter` to map claims to authorities (see above)
4. Protect endpoints using `hasAuthority("permission:user:read")` or `hasRole("ADMIN")`

The consuming service **never needs to share a database** with the Identity Service. JWT validation is fully local after the public key is cached.

---

## Sample API Calls

### Login
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Admin@1234!"}'
```

### Get Current User
```bash
curl http://localhost:8080/api/v1/auth/me \
  -H 'Authorization: Bearer <ACCESS_TOKEN>'
```

### Refresh Token
```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<REFRESH_TOKEN>"}'
```

### Create User (admin only)
```bash
curl -X POST http://localhost:8080/api/v1/users \
  -H 'Authorization: Bearer <ADMIN_ACCESS_TOKEN>' \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","email":"alice@example.com","password":"Secure@1234"}'
```

### Assign Role
```bash
curl -X PUT "http://localhost:8080/api/v1/users/{userId}/roles/{roleId}" \
  -H 'Authorization: Bearer <ADMIN_ACCESS_TOKEN>'
```

### Forgot Password
```bash
curl -X POST http://localhost:8080/api/v1/auth/forgot-password \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com"}'
```
