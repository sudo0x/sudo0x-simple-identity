# Questions & Answers

---

## 1. Why does this Identity Service not use Spring Authorization Server?

**Short answer:** Spring Authorization Server (SAS) solves a different problem. Using it here would be the wrong tool for the job.

### What Spring Authorization Server actually is

SAS is a full implementation of **OAuth 2.1** and **OpenID Connect 1.0**. It is designed for this scenario:

```
Third-party app (client)
        │
        │  "I want to act on behalf of this user"
        ▼
Spring Authorization Server
        │  authorization_code flow, consent screen, redirect URIs
        ▼
Issues an OAuth access token scoped to what the client is allowed to do
```

OAuth was designed for **delegated authorization** — "let me use my Google account to log into your app." It is the right choice when:

- Multiple third-party applications integrate with your platform
- You need SSO across many apps (OIDC)
- You need social login / federation
- You need fine-grained scope-based authorization between services
- You are building a platform like Google, GitHub, or Okta

### Why it does not fit this project

This Identity Service is a **dedicated authentication backend for a single application** — not a platform that grants access to third parties. The users are the application's own users. There are no external OAuth clients, no redirect URIs, no consent screens, no scopes.

| OAuth / SAS | This project |
|---|---|
| Multi-tenant, multi-client | Single application |
| authorization_code, device_code, client_credentials flows | Username + password login |
| Redirect-based (browser redirect to auth server) | Direct API call (no redirect) |
| Scopes (read:email, write:profile) | Permissions (user:read, role:create) |
| Consent screen | No consent — users are the app's own users |

Adding SAS would mean managing OAuth clients, scopes, redirect URIs, and OIDC discovery documents for a use case that needs none of it. That is complexity without benefit.

### When you SHOULD use Spring Authorization Server

- You are building a **platform** that multiple applications or third-party developers integrate with
- You need **SSO** — one login works across many apps
- You need **social login** (Google, GitHub, Apple)
- You are replacing something like Auth0, Okta, or Keycloak
- Your microservices need to authorize **machine-to-machine** calls using client_credentials

### Industry reality

Small to mid-scale companies commonly run a simple custom JWT auth service exactly like this one. Large platforms (AWS Cognito, Google Identity, Okta) use full OAuth/OIDC. The right choice depends on your requirements — not on which is more "enterprise-sounding."

If this project ever needs to support third-party OAuth integrations or SSO, the correct move is to put Keycloak, Auth0, or Spring Authorization Server **in front of** this service — not to rewrite this service into one.

---

## 2. Why is there no public registration endpoint?

**Short answer:** User registration is a **business process**, not an infrastructure operation. This Identity Service is infrastructure. Registration belongs in the application layer.

### The difference between authentication and registration

Authentication answers: *"Is this person who they claim to be?"*
Registration answers: *"Is this person allowed to have an account here?"*

The second question is business logic:

- Is this an invite-only product? The invite must be validated first.
- Is this a paid SaaS? The payment must be confirmed before creating the account.
- Is this a B2B product? The company's admin approves employees.
- Is this a public consumer app? You still need CAPTCHA, rate limiting, disposable-email detection, fraud checks.

None of this logic belongs inside an identity service. The identity service should not know whether your business allows open registration, invite-only, or admin-provisioned accounts.

### How registration actually works in this architecture

```
User fills out sign-up form
        │
        ▼
Your Application (API gateway, BFF, or backend)
        │  runs business rules:
        │    - validate invite code
        │    - check payment status
        │    - rate limit by IP
        │    - verify CAPTCHA
        │    - check disposable email lists
        │
        ▼  calls Identity Service with a service account token
POST /api/v1/users  (requires user:create permission)
        │
        ▼
User is created in Identity Service with USER role
        │
        ▼
Application sends welcome email, sets up onboarding, etc.
```

The identity service creates the account — but the **decision to allow the account** is made by your application, not by the identity service.

### Why a public `/register` endpoint is dangerous without safeguards

An open registration endpoint exposes you to:

| Attack | Impact |
|---|---|
| Spam account creation (bots) | DB pollution, abuse |
| User enumeration via error messages | Reveals which emails are registered |
| Brute-force registration | Resource exhaustion |
| Disposable email abuse | Hard-to-moderate fake accounts |
| Privilege escalation | If `role` is accepted as a parameter |

A naive `POST /register { username, email, password }` with none of these mitigations is a security hole, not a feature.

### If you DO need a self-registration endpoint

Add it — but with proper controls. The endpoint belongs in this service, but it must be hardened:

```java
@PostMapping("/register")  // public, no auth required
public ResponseEntity<...> register(@Valid @RequestBody RegisterRequest request) {
    // 1. Only ever assigns USER role (hardcoded — never accept role from input)
    // 2. Requires email verification before the account can be used
    // 3. Should be rate-limited by IP (add a rate-limit filter or API gateway policy)
    // 4. Should have CAPTCHA in the frontend
}
```

Required additions before enabling public registration:
- [ ] Email verification enforced (`email.verification-required=true`)
- [ ] Rate limiting (API gateway, or a library like Bucket4j)
- [ ] Username/email input sanitization (already done via validation)
- [ ] `role` is never accepted from user input (always assign `USER` role only)
- [ ] Optionally: disposable email domain blocklist

### Industry standard patterns

| Application type | Registration pattern |
|---|---|
| Internal tooling / enterprise | Admin creates users (`POST /users` with admin token) |
| B2B SaaS | Invite-based — app validates invite, then calls identity service |
| B2C with controlled growth | Admin-approved or waitlist, then identity service creates account |
| Open consumer app | Public `/register` with rate limiting + email verification + CAPTCHA |
| SSO platform | Delegate to OAuth provider (Google, GitHub) — no local password at all |

This project defaults to the safest option: **admin-provisioned users**. The `POST /api/v1/users` endpoint (protected by `user:create` permission) is how accounts are created. Your application calls it from a secure backend context with a service account JWT.
