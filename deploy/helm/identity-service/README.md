# Identity Service — Helm Chart

Production-ready Helm chart for deploying the Identity Service on Kubernetes.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Chart Structure](#2-chart-structure)
3. [Configuration Reference](#3-configuration-reference)
4. [Secrets](#4-secrets)
5. [JWT Signing Key Setup](#5-jwt-signing-key-setup)
6. [PostgreSQL Configuration](#6-postgresql-configuration)
7. [Local Installation](#7-local-installation)
8. [Development Installation](#8-development-installation)
9. [Staging Installation](#9-staging-installation)
10. [Production Installation](#10-production-installation)
11. [Ingress / TLS](#11-ingress--tls)
12. [Cookie Configuration](#12-cookie-configuration)
13. [Scaling](#13-scaling)
14. [Health Checks](#14-health-checks)
15. [NetworkPolicy](#15-networkpolicy)
16. [Pod Security](#16-pod-security)
17. [Troubleshooting](#17-troubleshooting)
18. [Upgrade](#18-upgrade)
19. [Rollback](#19-rollback)
20. [Uninstall](#20-uninstall)
21. [Helm Tests](#21-helm-tests)

---

## 1. Prerequisites

| Requirement | Version |
|---|---|
| Kubernetes | >= 1.25 |
| Helm | >= 3.10 |
| PostgreSQL | >= 14 (external) |

The Identity Service requires an **external PostgreSQL** database. This chart does not bundle PostgreSQL. See [Section 6](#6-postgresql-configuration).

---

## 2. Chart Structure

```
deploy/helm/identity-service/
├── Chart.yaml
├── values.yaml              # Safe defaults — no secrets
├── values-local.yaml        # Local dev overrides
├── values-dev.yaml          # Shared dev cluster overrides
├── values-staging.yaml      # Staging overrides
├── values-prod.yaml         # Production overrides
├── .helmignore
├── README.md                # This file
└── templates/
    ├── _helpers.tpl          # Template helpers
    ├── deployment.yaml       # Main Deployment
    ├── service.yaml          # ClusterIP Service
    ├── serviceaccount.yaml   # ServiceAccount
    ├── configmap.yaml        # Non-sensitive config
    ├── secret.yaml           # Chart-managed Secret (optional)
    ├── ingress.yaml          # Ingress (optional)
    ├── hpa.yaml              # HorizontalPodAutoscaler (optional)
    ├── pdb.yaml              # PodDisruptionBudget (optional)
    ├── networkpolicy.yaml    # NetworkPolicy (optional)
    ├── helm-test.yaml        # Helm test Job
    └── NOTES.txt             # Post-install notes
```

---

## 3. Configuration Reference

All non-sensitive configuration is stored in a ConfigMap. Key values:

| Value | Default | Description |
|---|---|---|
| `image.repository` | `identity-service` | Container image repository |
| `image.tag` | `latest` | Image tag |
| `replicaCount` | `1` | Number of replicas |
| `config.springProfile` | `prod` | Spring Boot active profile |
| `config.database.host` | `postgres` | PostgreSQL hostname |
| `config.database.port` | `5432` | PostgreSQL port |
| `config.database.name` | `identity` | Database name |
| `config.database.username` | `identity` | Database username |
| `config.jwt.issuer` | `identity-service` | JWT issuer claim |
| `config.jwt.accessTokenExpiration` | `PT15M` | Access token TTL (ISO-8601) |
| `config.jwt.refreshTokenExpiration` | `P30D` | Refresh token TTL (ISO-8601) |
| `config.jwt.privateKeyPath` | `/app/keys/private.pem` | Path inside container for private key |
| `config.jwt.publicKeyPath` | `/app/keys/public.pem` | Path inside container for public key |
| `config.security.maxLoginAttempts` | `5` | Max failed logins before lock |
| `config.security.lockDuration` | `PT15M` | Account lock duration |
| `config.security.corsAllowedOrigins` | `""` | Comma-separated allowed CORS origins |
| `config.cookie.enabled` | `false` | Enable cookie-based auth |
| `config.cookie.secure` | `true` | Require HTTPS for cookies |
| `config.cookie.sameSite` | `Lax` | SameSite cookie attribute |
| `config.seed.enabled` | `false` | Seed admin user on startup |
| `ingress.enabled` | `false` | Enable Ingress |
| `autoscaling.enabled` | `false` | Enable HPA |
| `pdb.enabled` | `false` | Enable PodDisruptionBudget |
| `networkPolicy.enabled` | `false` | Enable NetworkPolicy |

Use `helm show values deploy/helm/identity-service` to see all defaults.

---

## 4. Secrets

The chart supports two modes:

### Option A — Chart-managed Secret (dev / local only)

```yaml
# values-dev.yaml
secret:
  existingSecret: ""
  databasePassword: "my-dev-password"
```

Or via `--set`:
```bash
helm upgrade --install identity-service deploy/helm/identity-service \
  --set secret.databasePassword=my-dev-password
```

> **Warning**: Never use this for staging or production — values may end up in Helm history.

### Option B — External Secret (recommended for production)

Create the Secret independently before installing the chart:

```bash
kubectl create secret generic identity-service-secrets \
  --from-literal=DATABASE_PASSWORD='<your-db-password>' \
  --from-file=jwt-private-key=./private.pem \
  --from-file=jwt-public-key=./public.pem \
  -n identity
```

Then reference it:

```yaml
secret:
  existingSecret: "identity-service-secrets"
```

The chart will **not** create a Secret when `existingSecret` is set.

Expected Secret keys:

| Key | Description |
|---|---|
| `DATABASE_PASSWORD` | PostgreSQL password |
| `jwt-private-key` | RSA-2048 private key PEM content |
| `jwt-public-key` | RSA-2048 public key PEM content |

---

## 5. JWT Signing Key Setup

The Identity Service uses **RSA-2048 asymmetric signing**.

### Generate a key pair

```bash
# Generate private key
openssl genrsa -out private.pem 2048

# Extract public key
openssl rsa -in private.pem -pubout -out public.pem
```

### Store in Kubernetes Secret

```bash
kubectl create secret generic identity-service-secrets \
  --from-literal=DATABASE_PASSWORD='<password>' \
  --from-file=jwt-private-key=./private.pem \
  --from-file=jwt-public-key=./public.pem \
  -n identity
```

The chart mounts these as files at `/app/keys/private.pem` and `/app/keys/public.pem` inside the container, with appropriate read permissions (0400 for private, 0444 for public).

### Local development mode

Set `config.jwt.privateKeyPath: generate` to use ephemeral in-memory keys:

```yaml
config:
  jwt:
    privateKeyPath: generate
    publicKeyPath: generate
```

> **Warning**: Ephemeral keys mean all tokens are invalidated on pod restart. Never use in production.

### Key rotation

To rotate JWT keys:

1. Generate a new key pair.
2. Update the Kubernetes Secret with the new keys.
3. Perform a rolling restart:
   ```bash
   kubectl rollout restart deployment/identity-service -n identity
   ```
4. Existing valid access tokens (signed with the old key) will be rejected after rotation. Clients must re-authenticate.
5. Refresh tokens are stored in the database and use database-side validation, so refresh token rotation is unaffected.

---

## 6. PostgreSQL Configuration

This chart treats PostgreSQL as an **external dependency**.

Supported options:

- **Managed PostgreSQL** (AWS RDS, GCP Cloud SQL, Azure Database for PostgreSQL) — recommended
- **Standalone PostgreSQL Deployment** in the same cluster
- **Bitnami PostgreSQL Helm chart** deployed separately

Set the connection details:

```yaml
config:
  database:
    host: my-postgres.example.com
    port: 5432
    name: identity
    username: identity
```

The password comes from the Secret (see [Section 4](#4-secrets)).

**Flyway migrations** run automatically on startup. The application will refuse to start if the database is unreachable or if migrations fail. This is intentional — it prevents running a service against a stale or inconsistent schema.

During rolling upgrades, new pods apply any pending Flyway migrations before serving traffic. Ensure your Flyway migrations are backwards-compatible with the running version of the application during the upgrade window.

---

## 7. Local Installation

Requires a local Kubernetes cluster (kind, minikube, k3d) with PostgreSQL available.

```bash
# Build the Docker image locally
docker build -t identity-service:latest .

# Load into kind (if using kind)
kind load docker-image identity-service:latest

# Install
helm upgrade --install identity-service \
  deploy/helm/identity-service \
  -n identity \
  --create-namespace \
  -f deploy/helm/identity-service/values-local.yaml

# Port-forward to access locally
kubectl port-forward svc/identity-service 8080:8080 -n identity
```

The local values use:
- `image.pullPolicy: Never` (uses locally-built image)
- `secret.databasePassword: localdevpassword` (non-production placeholder)
- `config.jwt.privateKeyPath: generate` (ephemeral keys)
- `config.seed.enabled: true` (creates default admin user)
- Single replica, reduced resources

---

## 8. Development Installation

For a shared development namespace:

```bash
# Create namespace
kubectl create namespace identity-dev

# Create secrets
kubectl create secret generic identity-service-secrets \
  --from-literal=DATABASE_PASSWORD='dev-password' \
  --from-file=jwt-private-key=./private.pem \
  --from-file=jwt-public-key=./public.pem \
  -n identity-dev

# Install
helm upgrade --install identity-service \
  deploy/helm/identity-service \
  -n identity-dev \
  --create-namespace \
  -f deploy/helm/identity-service/values-dev.yaml
```

---

## 9. Staging Installation

```bash
# Create namespace
kubectl create namespace identity-staging

# Create TLS secret (or use cert-manager)
kubectl create secret tls identity-service-staging-tls \
  --cert=staging.crt \
  --key=staging.key \
  -n identity-staging

# Create application secrets
kubectl create secret generic identity-service-secrets \
  --from-literal=DATABASE_PASSWORD='<staging-db-password>' \
  --from-file=jwt-private-key=./private.pem \
  --from-file=jwt-public-key=./public.pem \
  -n identity-staging

# Install
helm upgrade --install identity-service \
  deploy/helm/identity-service \
  -n identity-staging \
  --create-namespace \
  -f deploy/helm/identity-service/values-staging.yaml
```

---

## 10. Production Installation

```bash
# Create namespace
kubectl create namespace identity

# Create TLS secret (or use cert-manager)
kubectl create secret tls identity-service-tls \
  --cert=prod.crt \
  --key=prod.key \
  -n identity

# Create application secrets (NEVER put real values in values files)
kubectl create secret generic identity-service-secrets \
  --from-literal=DATABASE_PASSWORD='<your-prod-db-password>' \
  --from-file=jwt-private-key=./private.pem \
  --from-file=jwt-public-key=./public.pem \
  -n identity

# Install
helm upgrade --install identity-service \
  deploy/helm/identity-service \
  -n identity \
  --create-namespace \
  -f deploy/helm/identity-service/values-prod.yaml

# Verify
kubectl get pods -n identity
kubectl get svc -n identity
kubectl get ingress -n identity
kubectl describe deployment identity-service -n identity
kubectl logs -n identity deployment/identity-service --tail=100
```

---

## 11. Ingress / TLS

Enable Ingress in your values file:

```yaml
ingress:
  enabled: true
  className: nginx
  host: identity.example.com
  path: /
  pathType: Prefix
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
  tls:
    enabled: true
    secretName: identity-service-tls
```

**cert-manager** (recommended for automatic TLS):

```yaml
ingress:
  enabled: true
  className: nginx
  host: identity.example.com
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod
  tls:
    enabled: true
    secretName: identity-service-tls
```

**Behind a reverse proxy / load balancer:**

When the Identity Service is deployed behind an Ingress controller that terminates TLS, configure the cookie settings accordingly:

```yaml
config:
  cookie:
    enabled: true
    secure: true      # Always true — the browser connection is HTTPS even if internal is HTTP
    sameSite: Lax
    domain: "example.com"
```

The `Secure` cookie flag is determined by the browser's connection to the Ingress, not the internal HTTP connection between Ingress and pod.

---

## 12. Cookie Configuration

The Identity Service supports optional cookie-based authentication for browser clients.

When `cookie.enabled=true`, auth endpoints set HttpOnly cookies **in addition to** returning tokens in the response body. This supports both SPA (using Bearer header) and SSR clients (using cookies).

```yaml
config:
  cookie:
    enabled: true
    secure: true        # Requires HTTPS (set false only for local HTTP dev)
    sameSite: Lax       # Lax: safe default; Strict: stricter CSRF protection
    domain: ""          # Empty = current domain only; set to share across subdomains
    path: /
```

**SameSite guidance:**
- `Lax` — recommended default; protects against CSRF for most use cases
- `Strict` — strongest protection; may break OAuth redirects and cross-site navigation
- `None` — required for cross-site iframes; must be combined with `secure: true`

**Cookie names** (configurable):
- `access_token` — short-lived JWT
- `refresh_token` — long-lived refresh token

Both are `HttpOnly` (not accessible to JavaScript) and `Secure` (only sent over HTTPS).

**Behind Ingress:** Cookies flow correctly through Nginx/Traefik ingress controllers. The `Secure` flag is applied by the browser based on the TLS connection to the ingress controller.

---

## 13. Scaling

### Horizontal Pod Autoscaler

```yaml
autoscaling:
  enabled: true
  minReplicas: 2
  maxReplicas: 10
  targetCPUUtilizationPercentage: 70
```

The Identity Service is **stateless** from the Kubernetes perspective:
- Access token validation is stateless (RSA signature verification)
- Refresh token state is stored in PostgreSQL
- Account lock state is stored in PostgreSQL
- Session state is stored in PostgreSQL

Multiple replicas are safe to run concurrently. The chart uses `PessimisticLocking` on refresh token rotation to prevent race conditions under concurrent refresh requests.

### Pod Disruption Budget

```yaml
pdb:
  enabled: true
  minAvailable: 1
```

> **Note**: With `replicaCount: 1` and `pdb.minAvailable: 1`, voluntary disruptions (node drain, upgrades) will be blocked. Increase `replicaCount` before enabling PDB in production.

### Rolling Updates

The chart uses `RollingUpdate` strategy with `maxUnavailable: 0`, ensuring zero downtime during deployments:

```yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxSurge: 1
    maxUnavailable: 0
```

---

## 14. Health Checks

The chart uses Spring Boot Actuator endpoints for Kubernetes probes.

| Probe | Path | Purpose |
|---|---|---|
| `startupProbe` | `/actuator/health/liveness` | Waits for app to finish starting |
| `readinessProbe` | `/actuator/health/readiness` | Traffic only sent when ready |
| `livenessProbe` | `/actuator/health/liveness` | Restarts if app is stuck |

**Readiness vs Liveness:**
- Readiness includes database connectivity — pod is removed from Service endpoints if DB is unavailable
- Liveness is application-only — Kubernetes will not restart a pod just because the database is down; it waits for the database to recover

This prevents cascading restarts during transient database outages.

**Startup probe** gives the app sufficient time to start (up to `failureThreshold × periodSeconds` = 100 seconds by default) before readiness/liveness probes begin.

---

## 15. NetworkPolicy

The optional NetworkPolicy restricts traffic to:

```
Ingress Controller (or allowed clients)
         ↓
  Identity Service (port 8080)
         ↓
     PostgreSQL (port 5432)
```

```yaml
networkPolicy:
  enabled: true
  ingressPodSelector:
    matchLabels:
      app.kubernetes.io/name: ingress-nginx
  databasePort: 5432
```

> **Note**: Network policy implementation depends on the CNI plugin (Calico, Cilium, Weave, etc.). Test in your specific cluster environment. Adjust `ingressPodSelector` to match your Ingress controller's pod labels.

DNS egress (port 53 UDP/TCP) is always permitted.

---

## 16. Pod Security

The chart applies Kubernetes security best practices:

**Pod Security Context:**
```yaml
podSecurityContext:
  runAsNonRoot: true
  runAsUser: 1000
  runAsGroup: 1000
  fsGroup: 1000
  seccompProfile:
    type: RuntimeDefault
```

**Container Security Context:**
```yaml
securityContext:
  allowPrivilegeEscalation: false
  readOnlyRootFilesystem: true
  capabilities:
    drop:
      - ALL
```

**Read-only root filesystem:** The container filesystem is read-only. A writable `emptyDir` volume is mounted at `/tmp` for Spring Boot temporary files (configurable via `tmpVolume`).

**JWT key permissions:** The private key is mounted with mode `0400` (owner read-only). The public key is mounted with mode `0444`.

**ServiceAccount:** A dedicated ServiceAccount is created with `automountServiceAccountToken: false` — the application does not need Kubernetes API access.

---

## 17. Troubleshooting

**Pod stuck in `Pending`:**
```bash
kubectl describe pod -n identity -l app.kubernetes.io/name=identity-service
```
Check for insufficient resources or unsatisfied node selectors/tolerations.

**Pod stuck in `Init` or `CrashLoopBackOff`:**
```bash
kubectl logs -n identity deployment/identity-service --previous
```
Common causes: database unreachable, Flyway migration failure, missing Secret key.

**Pod failing readiness:**
```bash
kubectl describe pod -n identity <pod-name>
# Check the "Events" and "Conditions" sections
```

**Check ConfigMap:**
```bash
kubectl get configmap identity-service-config -n identity -o yaml
```

**Check Secret (keys only, not values):**
```bash
kubectl get secret identity-service-secrets -n identity -o jsonpath='{.data}' | python3 -c "import sys,json; [print(k) for k in json.load(sys.stdin)]"
```

**Database connection issues:**
```bash
kubectl exec -n identity deployment/identity-service -- \
  wget -q -O - http://localhost:8080/actuator/health | python3 -m json.tool
```

**View application logs:**
```bash
kubectl logs -n identity deployment/identity-service -f --tail=200
```

**Check Flyway migration status:**
The application logs Flyway migration results at startup. Look for lines containing `Flyway` in the startup logs.

---

## 18. Upgrade

```bash
# Upgrade to a new image tag
helm upgrade identity-service deploy/helm/identity-service \
  -n identity \
  -f deploy/helm/identity-service/values-prod.yaml \
  --set image.tag=1.1.0

# Check rollout status
kubectl rollout status deployment/identity-service -n identity

# View revision history
helm history identity-service -n identity
```

**Database migrations during upgrade:**
Flyway runs automatically on startup. New pods apply pending migrations before becoming ready. The `readinessProbe` ensures traffic is only routed to pods that have finished migrating. Write backwards-compatible migrations — during a rolling update, old and new pod versions may run simultaneously.

---

## 19. Rollback

```bash
# List revisions
helm history identity-service -n identity

# Roll back to a specific revision
helm rollback identity-service <REVISION> -n identity

# Roll back to the previous revision
helm rollback identity-service -n identity
```

> **Database note**: Helm rollback reverts the Kubernetes resources (Deployment, ConfigMap, etc.) to the previous state but does **not** revert database migrations. If you applied a destructive migration, you must apply a compensating migration manually. Design migrations to be reversible or additive where possible.

---

## 20. Uninstall

```bash
helm uninstall identity-service -n identity
```

The chart-managed Secret has `helm.sh/resource-policy: keep`, so it is retained after uninstall to prevent accidental secret deletion. Delete it manually if needed:

```bash
kubectl delete secret identity-service-secrets -n identity
```

Other retained resources (PVCs if any, namespace) must also be deleted manually.

---

## 21. Helm Tests

The chart includes a test that verifies the deployed service is reachable:

```bash
helm test identity-service -n identity
```

The test checks:
1. Liveness endpoint (`/actuator/health/liveness`) returns `status: UP`
2. Readiness endpoint (`/actuator/health/readiness`) returns `status: UP`
3. JWKS endpoint (`/.well-known/jwks.json`) returns a valid key set

View test logs:
```bash
kubectl logs -n identity identity-service-test
```

The test pod is automatically deleted on success (`hook-delete-policy: before-hook-creation,hook-succeeded`).
