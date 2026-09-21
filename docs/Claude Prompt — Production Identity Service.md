# Production-Grade Spring Boot Identity Service

You are a senior backend architect and Spring Boot engineer.

Build a **complete, production-ready standalone Identity Service** that will become a reusable authentication/authorization microservice for my future Spring Boot applications.

Do not generate a toy/demo project. Generate a real project with production-quality architecture, security, database migrations, tests, Docker support, configuration, documentation, error handling, observability, and clean code.

---

# 1. Business Context

This Identity Service will be deployed independently.

Future applications will communicate with it through REST APIs.

Examples of future applications:

- E-commerce
- Inventory
- HR
- CRM
- Internal business applications

Each application will have its **own deployment/instance of the Identity Service and its own database**.

Important:

- This is NOT a SaaS identity platform.
- This is NOT multi-tenant.
- Users are NOT shared across different applications.
- Do NOT implement cross-application identity.
- Do NOT implement OAuth2/OIDC in v1.
- Do NOT implement social login in v1.
- Do NOT implement SSO in v1.
- Do NOT implement LDAP in v1.
- Do NOT implement SCIM in v1.
- Do NOT implement passkeys in v1.
- Do NOT implement MFA in v1 unless you create a clean extension point but leave it disabled/not implemented.
- Use a straightforward JWT-based authentication architecture.

The goal is a reusable, independent Identity Service, not a general-purpose identity SaaS product.

---

# 2. Technology Stack

Use current stable versions compatible with each other.

Prefer:

- Java 21 LTS
- Spring Boot 3.x latest stable compatible version
- Spring Security 6.x
- Spring Data JPA
- Hibernate
- PostgreSQL
- Flyway
- Maven
- Bean Validation
- Docker
- Docker Compose
- JUnit 5
- Mockito
- Testcontainers
- Spring Boot Actuator

Use Maven unless there is a strong architectural reason otherwise.

Do not use unnecessary dependencies.

Keep the project maintainable and conventional.

---

# 3. Architecture

Use a modular, feature-oriented architecture.

Do NOT organize the entire application only as:

```text
controller/
service/
repository/
entity/
dto/
```

Instead organize by business capability.

Preferred structure:

```text
com.example.identity
│
├── IdentityServiceApplication
│
├── common
│   ├── exception
│   ├── response
│   ├── validation
│   └── security
│
├── user
│   ├── controller
│   ├── service
│   ├── repository
│   ├── entity
│   ├── dto
│   └── mapper
│
├── role
│   ├── controller
│   ├── service
│   ├── repository
│   ├── entity
│   ├── dto
│   └── mapper
│
├── permission
│   ├── controller
│   ├── service
│   ├── repository
│   ├── entity
│   ├── dto
│   └── mapper
│
├── authentication
│   ├── controller
│   ├── service
│   ├── dto
│   └── ...
│
├── credential
│   ├── service
│   └── ...
│
└── session
    ├── service
    ├── repository
    ├── entity
    └── ...
```

You may adjust the structure if you have a better production architecture, but preserve clear domain boundaries.

---

# 4. Core Responsibilities

The service owns:

1. Users
2. Credentials/passwords
3. Authentication
4. JWT access tokens
5. Refresh tokens
6. Sessions
7. Roles
8. Permissions
9. Password management
10. Account status
11. Security-related audit information

The service must NOT own:

- Orders
- Products
- Payments
- Inventory
- Employees
- Invoices
- Business workflows
- Domain-specific business rules

Keep the Identity Service generic.

---

# 5. Domain Model

Implement the following core concepts.

## User

Fields should include appropriate production fields such as:

```text
id
username
email
status
emailVerified
createdAt
updatedAt
```

Use UUID as the primary identifier unless you have a strong reason to choose another strategy.

User status should support at least:

```text
ACTIVE
INACTIVE
LOCKED
```

Consider whether `DELETED` should be a status or whether soft deletion should be handled differently.

Do not store plaintext passwords.

---

# 6. Credential

Credentials must be separate from User.

Example:

```text
Credential
----------
id
userId
passwordHash
createdAt
updatedAt
```

Passwords must be hashed using a strong password hashing algorithm supported by Spring Security.

Prefer Argon2id if practical.

If BCrypt is chosen, configure an appropriate work factor.

Never log:

- passwords
- password hashes
- access tokens
- refresh tokens
- secrets

---

# 7. Role

Implement RBAC.

Example roles:

```text
ADMIN
MANAGER
USER
```

Do not hardcode these roles into Java business logic.

Roles should be database entities.

Fields:

```text
id
name
description
createdAt
updatedAt
```

Role names must be unique.

---

# 8. Permission

Permissions should also be database entities.

Example:

```text
user:read
user:create
user:update
user:delete

role:read
role:create
role:update
role:delete
```

Fields:

```text
id
code
description
createdAt
updatedAt
```

Permission code must be unique.

Do not hardcode business-specific permissions such as:

```text
order:approve
inventory:adjust
invoice:approve
```

The Identity Service must remain generic.

The consuming application may define and use application-specific permissions.

---

# 9. Relationships

Implement:

```text
User
 |
 ├── Credential
 |
 ├── Roles
 |     |
 |     └── Permissions
 |
 └── Sessions / Refresh Tokens
```

Database relationships:

```text
users
credentials

roles
permissions

user_roles
role_permissions

refresh_tokens
```

Avoid JPA relationship designs that cause accidental huge object graphs.

Be careful with:

- lazy loading
- serialization
- N+1 queries
- cascade behavior
- orphan removal

Do not expose entities directly from REST controllers.

---

# 10. Authentication

Implement:

```http
POST /api/v1/auth/login
```

Request:

```json
{
  "username": "john",
  "password": "password"
}
```

Response should contain:

```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

Authentication flow:

```text
username/password
       ↓
find user
       ↓
check account status
       ↓
verify password
       ↓
load roles/permissions
       ↓
generate access token
       ↓
generate refresh token
       ↓
persist refresh-token session
       ↓
return tokens
```

Do not reveal whether a username or email exists when handling authentication failures if doing so would create user enumeration risk.

Use generic authentication failure responses.

---

# 11. JWT

Use JWT for access tokens.

Prefer asymmetric signing.

For example:

```text
Identity Service
      |
      | private key
      ↓
sign JWT
      |
      ↓
Access Token
```

Other services will validate using the public key.

Do NOT distribute the private signing key to consuming services.

JWT should contain only appropriate claims.

Example:

```json
{
  "sub": "user-uuid",
  "username": "john",
  "roles": ["ADMIN"],
  "permissions": [
    "user:read",
    "user:update"
  ],
  "iat": 1234567890,
  "exp": 1234568790
}
```

Use configurable expiration.

Default access-token expiration can be around 15 minutes.

Do not put sensitive personal information in the JWT.

Implement a clean abstraction such as:

```text
JwtTokenService
```

or equivalent.

Do not scatter JWT generation logic throughout the application.

---

# 12. Refresh Tokens

Implement secure refresh-token rotation.

Do NOT store plaintext refresh tokens in the database.

Store a cryptographic hash.

Example:

```text
refresh_tokens
---------------
id
user_id
token_hash
expires_at
revoked_at
created_at
```

Refresh flow:

```http
POST /api/v1/auth/refresh
```

Request:

```json
{
  "refreshToken": "..."
}
```

Validate:

1. Token format
2. Token hash
3. Session existence
4. Expiration
5. Revocation status
6. User status

When refreshing:

- revoke the old refresh token
- issue a new refresh token
- issue a new access token

Implement refresh-token rotation.

Consider token reuse detection.

If a previously rotated/revoked refresh token is reused, invalidate the related session/token family if appropriate.

Design this cleanly without overcomplicating v1.

---

# 13. Logout

Implement:

```http
POST /api/v1/auth/logout
```

The logout operation should revoke the relevant refresh-token session.

Also support:

```http
POST /api/v1/auth/logout-all
```

to revoke all active sessions for the authenticated user.

Access JWTs should remain short-lived rather than requiring centralized storage for every access token.

---

# 14. Current User

Implement:

```http
GET /api/v1/me
```

Return information about the authenticated user.

Example:

```json
{
  "id": "...",
  "username": "john",
  "email": "john@example.com",
  "status": "ACTIVE",
  "roles": [
    "USER"
  ],
  "permissions": [
    "user:read"
  ]
}
```

Do not return:

- password
- password hash
- refresh token
- internal secrets

---

# 15. Password Management

Implement:

```http
POST /api/v1/auth/change-password
POST /api/v1/auth/forgot-password
POST /api/v1/auth/reset-password
```

Password reset must use a secure, expiring, single-use token.

Never store reset tokens in plaintext.

Use hashed reset tokens where practical.

Never expose whether an email/account exists through password-reset responses.

Example response:

```text
If the account exists, a password reset instruction has been sent.
```

Make email delivery an abstraction so it can later integrate with the Notification Service.

For v1, provide a development implementation/logging mechanism without exposing secrets in production logs.

---

# 16. Email Verification

Implement email verification as a clean extension of the authentication flow.

Potential endpoint:

```http
POST /api/v1/auth/verify-email
```

Use:

- expiring token
- single-use token
- hashed token storage

Do not automatically assume an account is email-verified.

Make this behavior configurable.

---

# 17. User Management

Implement administrative CRUD APIs.

```http
GET    /api/v1/users
GET    /api/v1/users/{id}
POST   /api/v1/users
PATCH  /api/v1/users/{id}
DELETE /api/v1/users/{id}
```

Support:

- pagination
- sorting
- filtering
- username search
- email search
- status filtering

Never return credential information.

Avoid hard delete unless there is a clear reason.

Prefer a safe account-deactivation approach.

---

# 18. Role Management

Implement:

```http
GET    /api/v1/roles
GET    /api/v1/roles/{id}
POST   /api/v1/roles
PATCH  /api/v1/roles/{id}
DELETE /api/v1/roles/{id}
```

Provide APIs to assign/remove roles from users.

For example:

```http
PUT /api/v1/users/{userId}/roles/{roleId}
DELETE /api/v1/users/{userId}/roles/{roleId}
```

Prevent deleting roles that are still required/assigned unless the behavior is explicitly safe.

---

# 19. Permission Management

Implement:

```http
GET /api/v1/permissions
```

Administrative mutation APIs may be included if justified.

Avoid allowing arbitrary users to create security permissions unless explicitly authorized.

---

# 20. Authorization

Use Spring Security.

Protect endpoints appropriately.

At minimum:

```text
Public
------
POST /auth/login
POST /auth/refresh
POST /auth/forgot-password
POST /auth/reset-password
POST /auth/verify-email

Authenticated
-------------
GET /me
POST /auth/logout
POST /auth/logout-all
POST /auth/change-password

Admin
-----
User administration
Role administration
Permission administration
```

Do not simply put:

```java
.permitAll()
```

everywhere for convenience.

Implement actual authorization.

Use permission-based authorization where appropriate.

---

# 21. Password Security

Apply production password policies.

At minimum:

- minimum length
- reject obviously invalid passwords
- configurable requirements
- never log passwords
- never return passwords
- secure hashing
- timing-safe verification through the chosen password encoder

Do not make the password rules absurdly restrictive.

Make them configurable.

---

# 22. Brute Force Protection

Implement reasonable login protection.

For example:

```text
failed attempts
       ↓
threshold
       ↓
temporary lock
```

Avoid a design that permanently locks legitimate users because of simple mistakes.

Make:

- maximum failed attempts
- lock duration

configurable.

Consider whether this should be backed by Redis later.

For v1, a database-backed implementation is acceptable if designed cleanly.

---

# 23. API Response Format

Create a consistent API response format.

For example:

```json
{
  "success": true,
  "data": {},
  "message": null,
  "timestamp": "2026-01-01T00:00:00Z",
  "requestId": "..."
}
```

For errors:

```json
{
  "success": false,
  "data": null,
  "message": "Validation failed",
  "timestamp": "...",
  "requestId": "...",
  "errors": [
    {
      "field": "email",
      "code": "INVALID_FORMAT",
      "message": "Invalid email address"
    }
  ]
}
```

Use appropriate HTTP status codes.

Implement global exception handling using Spring's recommended mechanisms.

Do not expose stack traces in production responses.

---

# 24. Validation

Use Jakarta Bean Validation.

Validate:

- username
- email
- passwords
- role names
- permission codes
- pagination parameters
- request bodies

Return structured validation errors.

---

# 25. Database

Use PostgreSQL.

Use Flyway migrations.

Do NOT rely on:

```properties
spring.jpa.hibernate.ddl-auto=create
```

for production.

Use:

```text
Flyway → schema ownership
Hibernate → validation
```

Prefer:

```properties
spring.jpa.hibernate.ddl-auto=validate
```

or equivalent production-safe behavior.

Create proper:

- indexes
- unique constraints
- foreign keys
- not-null constraints
- timestamps

Think about query performance.

Important indexes should include appropriate indexes for:

- username
- email
- refresh token hash
- role name
- permission code
- foreign keys

---

# 26. Database Migration

Create migrations such as:

```text
V1__create_users.sql
V2__create_credentials.sql
V3__create_roles_permissions.sql
V4__create_user_roles.sql
V5__create_role_permissions.sql
V6__create_refresh_tokens.sql
...
```

You may consolidate logically related migrations where appropriate.

Do not generate destructive migrations without explicit justification.

---

# 27. Auditing

Track:

```text
createdAt
updatedAt
```

Use Spring Data auditing where appropriate.

For security-sensitive operations, consider an audit model for:

```text
LOGIN_SUCCESS
LOGIN_FAILURE
PASSWORD_CHANGED
PASSWORD_RESET
USER_CREATED
USER_DISABLED
USER_LOCKED
ROLE_ASSIGNED
ROLE_REMOVED
LOGOUT
```

Do not store sensitive secrets in audit records.

Do not store raw passwords or raw tokens.

---

# 28. Logging

Use structured logging where practical.

Every request should have a correlation/request ID.

Example:

```text
X-Request-ID
```

If the client provides one, validate/use it appropriately; otherwise generate one.

Include it in:

- logs
- error responses

Never log:

- passwords
- JWTs
- refresh tokens
- reset tokens
- secret keys
- authorization headers

---

# 29. Observability

Add Spring Boot Actuator.

Expose appropriate endpoints such as:

```text
/actuator/health
/actuator/info
/actuator/metrics
```

Do not expose sensitive actuator endpoints publicly.

Include health checks for:

- application
- database

Design for future Prometheus integration.

---

# 30. Configuration

Use strongly typed configuration properties.

Example:

```yaml
identity:
  jwt:
    issuer: identity-service
    access-token-expiration: 15m
    refresh-token-expiration: 30d

  security:
    max-login-attempts: 5
    lock-duration: 15m

  password:
    minimum-length: 8
```

Do not hardcode:

- JWT secrets
- private keys
- database passwords
- SMTP credentials

Support environment variables.

Use profiles carefully:

```text
application.yml
application-local.yml
application-test.yml
application-prod.yml
```

Do not commit real production secrets.

---

# 31. JWT Key Management

For local development, provide a simple development mechanism.

For production, support loading signing keys securely from configuration/environment-mounted files.

Prefer an asymmetric key pair.

Do not generate a new signing key every time the application restarts in production.

The JWT signing configuration should be clearly documented.

Provide an endpoint for public-key discovery if useful for consuming services, for example:

```http
GET /.well-known/jwks.json
```

However, do not turn this into a full OAuth/OIDC implementation.

The goal is simply to allow other internal services to obtain the public verification key safely.

---

# 32. Security Headers and HTTP Security

Configure Spring Security appropriately.

Consider:

- CORS
- CSRF behavior for stateless APIs
- secure headers
- content type options
- frame options
- cache control

Do not blindly disable security features without explaining why.

For a bearer-token API, configure CSRF according to the actual authentication architecture.

CORS must be configurable and must not default to allowing every production origin.

---

# 33. API Versioning

Use:

```text
/api/v1/...
```

from the beginning.

Do not mix:

```text
/api/users
/api/v1/users
/api/v2/users
```

without a reason.

Design DTOs so API contracts can evolve without exposing persistence entities.

---

# 34. Pagination

Implement a standard pagination contract.

Example:

```http
GET /api/v1/users?page=0&size=20&sort=createdAt,desc
```

Response:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5
}
```

Protect against unreasonable page sizes.

---

# 35. Concurrency and Transactions

Use transactions appropriately.

Pay special attention to:

- refresh-token rotation
- login attempt updates
- role assignment
- user status changes

Avoid unnecessarily large transactions.

Think about race conditions.

For example, two simultaneous refresh requests must not both successfully rotate the same refresh token.

---

# 36. REST API Documentation

Use OpenAPI/Swagger.

Document:

- authentication endpoints
- request models
- response models
- error models
- security requirements
- examples

Swagger UI should be configurable and ideally disabled/restricted in production if appropriate.

---

# 37. Testing

Testing must be comprehensive.

Create:

### Unit tests

Test:

- authentication service
- JWT service
- password service
- refresh-token service
- user service
- role service
- permission service
- validation logic

### Repository tests

Test important queries.

### Integration tests

Use:

```text
Testcontainers + PostgreSQL
```

Do not rely exclusively on H2 because PostgreSQL behavior differs.

### API tests

Test:

```text
login success
login failure
locked account
refresh success
refresh token reuse
logout
logout-all
current user
user CRUD
role assignment
permission checks
password change
password reset
validation errors
unauthorized requests
forbidden requests
```

### Security tests

Explicitly test that:

- passwords are never returned
- unauthorized users cannot access admin endpoints
- expired JWTs fail
- malformed JWTs fail
- invalid refresh tokens fail
- revoked refresh tokens fail
- refresh token rotation works
- users cannot access another user's protected resources where applicable

---

# 38. Docker

Provide:

```text
Dockerfile
docker-compose.yml
```

Local Docker Compose should include:

```text
identity-service
postgres
```

Optionally:

```text
mailhog
```

for local email testing.

Use health checks.

Do not hardcode credentials that look like production secrets.

Use environment variables.

---

# 39. Production Readiness

Include:

- graceful shutdown
- connection pool configuration
- database health checks
- reasonable timeouts
- secure defaults
- structured logging
- request IDs
- metrics
- environment configuration
- container-friendly logging
- non-root Docker user if practical
- JVM/container configuration appropriate for production

Avoid premature complexity.

---

# 40. README

Create an excellent README.

It must explain:

1. What the Identity Service is
2. Architecture
3. Technology stack
4. Domain model
5. Authentication flow
6. JWT architecture
7. Refresh-token architecture
8. API documentation
9. Local development
10. Database setup
11. Environment variables
12. Docker usage
13. Running tests
14. Production deployment
15. Security considerations
16. How another Spring Boot service validates JWTs
17. How to obtain the public key
18. How to integrate a future application

Include example commands.

---

# 41. Example Consumer Integration

Create documentation showing how a future Spring Boot application can consume the Identity Service.

For example:

```text
inventory-service
       │
       │ Authorization: Bearer <JWT>
       ▼
Spring Security Resource Server
       │
       ▼
JWT validation using Identity Service public key
```

Explain how claims map to Spring Security authorities.

For example:

```text
permission:user:read
permission:user:update
role:ADMIN
```

Show a clean `SecurityFilterChain` example for the consuming application.

Do NOT make consuming services call the Identity Service on every API request.

---

# 42. Error Handling

Create domain-specific exceptions where useful.

Examples:

```text
UserNotFoundException
UsernameAlreadyExistsException
EmailAlreadyExistsException
InvalidCredentialsException
AccountLockedException
AccountDisabledException
InvalidRefreshTokenException
RefreshTokenExpiredException
RoleNotFoundException
PermissionNotFoundException
```

Map them to appropriate HTTP responses.

Do not leak sensitive information.

---

# 43. Idempotency and API Safety

Think about repeated requests.

For example:

- role assignment
- logout
- password reset
- refresh token rotation

Avoid accidental duplicate records.

Use database constraints where appropriate.

---

# 44. Performance

Avoid unnecessary database queries.

Especially watch for:

```text
N+1 role queries
N+1 permission queries
```

Use appropriate fetch strategies/query design.

Do not blindly use:

```java
FetchType.EAGER
```

everywhere.

Use projections/entity graphs/custom queries where justified.

---

# 45. Code Quality

Follow clean-code principles.

Use:

- constructor injection
- immutable DTOs where appropriate
- records where appropriate
- meaningful names
- small focused services
- clear transaction boundaries
- no field injection
- no giant service classes
- no giant controllers

Do not overuse design patterns just for the sake of patterns.

Avoid unnecessary abstractions.

---

# 46. Do NOT create

Do not introduce:

```text
❌ Microservice-to-microservice synchronous calls for basic authentication
❌ OAuth2 authorization server
❌ OIDC provider
❌ Multi-tenancy
❌ SSO
❌ Social login
❌ LDAP
❌ Kafka
❌ Redis
❌ Elasticsearch
❌ Kubernetes manifests
❌ Service mesh
```

unless there is a clear architectural requirement.

The first version should be robust but understandable.

If Redis would significantly improve brute-force protection or session management, design the interfaces so it can be added later, but don't force Redis into v1.

---

# 47. Important Architectural Principle

The Identity Service should be reusable because it provides a **stable capability**, not because it contains generic abstractions for everything.

Keep this boundary:

```text
                    Identity Service
                 ┌─────────────────────┐
                 │                     │
                 │ Users               │
                 │ Credentials         │
                 │ Authentication      │
                 │ JWT                 │
                 │ Sessions            │
                 │ Roles               │
                 │ Permissions         │
                 │ Security            │
                 │                     │
                 └──────────┬──────────┘
                            │
                         JWT/API
                            │
                            ▼
                 ┌─────────────────────┐
                 │ Business Application │
                 │                     │
                 │ Orders              │
                 │ Products            │
                 │ Payments            │
                 │ Inventory           │
                 │ etc.                │
                 └─────────────────────┘
```

Never put business-domain logic inside the Identity Service.

---

# 48. Deliverables

Generate the complete project, including:

```text
pom.xml

src/main/java/...
src/main/resources/...

Flyway migrations

application.yml
application-local.yml
application-test.yml

Dockerfile
docker-compose.yml

README.md

OpenAPI configuration

Unit tests

Integration tests

Testcontainers configuration

Security configuration

JWT implementation

Database configuration

Exception handling

Validation

Logging configuration

Actuator configuration
```

Also provide:

```text
.env.example
```

with placeholder values only.

---

# 49. Development Seed Data

Provide development-only seed data.

For example:

```text
ADMIN
USER
```

and reasonable permissions.

Create a development admin user only through a safe initialization mechanism.

Never put a real password in source control.

Document how to set the initial admin password through environment variables or a local setup command.

Make sure production does NOT automatically create a default admin account.

---

# 50. API Security Model

Use a clear distinction:

```text
PUBLIC
    login
    refresh
    forgot password
    reset password
    email verification

AUTHENTICATED
    me
    logout
    logout-all
    change password

ADMIN / PRIVILEGED
    user administration
    role administration
    permission administration
```

Implement actual permission checks.

For example:

```text
user:read
user:create
user:update
user:delete

role:read
role:create
role:update
role:delete
```

Avoid relying only on role names such as:

```text
if ADMIN
```

Use permissions where appropriate.

---

# 51. Final Architecture Review

Before generating the final code, reason through:

1. Authentication flow
2. Refresh-token rotation
3. JWT signing and verification
4. Password hashing
5. Account locking
6. User/role/permission relationships
7. Database constraints
8. Transaction boundaries
9. Race conditions
10. Security vulnerabilities
11. API consistency
12. Production configuration
13. Docker deployment
14. Test coverage
15. Future extensibility

Identify and fix architectural problems before presenting the final project.

---

# 52. Expected Output

Do not give me only a conceptual explanation.

Generate the **actual complete source code** for the project.

For every file:

```text
/path/to/File.java
```

then provide its complete contents.

Do not use placeholders such as:

```text
// implementation omitted
// add your code here
// etc.
```

Do not omit important files.

If the project is too large to output in one response, split it into logical phases and continue from where you stopped. Never replace missing code with pseudocode.

Start by showing:

1. Final architecture
2. Complete project tree
3. Dependency choices
4. Database schema/design
5. Security architecture

Then generate the implementation file-by-file.

At the end, provide:

- complete setup instructions
- environment variables
- database startup
- how to run locally
- how to run tests
- how to run with Docker
- sample API requests
- sample login flow
- sample JWT
- sample refresh flow
- instructions for integrating a future Spring Boot application

The resulting project should compile and tests should pass without requiring me to fill in missing implementation.