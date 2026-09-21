# Phase 8 — Helm Chart & Kubernetes Deployment

After completing Phases 1–7 and verifying that the Identity Service is fully implemented, compileable, tested, and Dockerized, create a **production-ready Helm chart** for deploying the Identity Service to Kubernetes.

The Helm chart must be complete and deployable. Do not provide pseudocode, placeholders, TODOs, or incomplete templates.

## 1. Helm Chart Structure

Create:

```text
deploy/
└── helm/
    └── identity-service/
        ├── Chart.yaml
        ├── values.yaml
        ├── values-local.yaml
        ├── values-dev.yaml
        ├── values-staging.yaml
        ├── values-prod.yaml
        ├── .helmignore
        ├── README.md
        └── templates/
            ├── _helpers.tpl
            ├── deployment.yaml
            ├── service.yaml
            ├── serviceaccount.yaml
            ├── configmap.yaml
            ├── secret.yaml
            ├── ingress.yaml
            ├── hpa.yaml
            ├── pdb.yaml
            ├── networkpolicy.yaml
            ├── helm-test.yaml
            └── NOTES.txt
```

You may adjust the structure if there is a better production-grade Helm organization, but keep it simple, maintainable, and reusable.

## 2. Helm Requirements

The chart must support:

- configurable Docker image repository/tag
- `imagePullPolicy`
- configurable replica count
- Kubernetes Service
- configurable Service type
- configurable container port
- resource requests and limits
- startup probe
- readiness probe
- liveness probe
- graceful shutdown
- termination grace period
- rolling deployments
- configurable environment variables
- ConfigMap for non-sensitive configuration
- Secret for sensitive configuration
- JWT signing key configuration
- PostgreSQL configuration
- database credentials
- Spring profiles
- Spring Boot configuration
- pod security context
- container security context
- non-root container execution
- `seccompProfile`
- read-only root filesystem where compatible
- dropped Linux capabilities
- ServiceAccount
- optional ServiceAccount token mounting
- optional Ingress
- configurable Ingress class
- configurable hostname
- TLS configuration
- optional HorizontalPodAutoscaler
- optional PodDisruptionBudget
- optional NetworkPolicy
- pod anti-affinity/topology spread configuration
- node selector
- tolerations
- affinity
- configurable annotations and labels

## 3. Configuration

Do not hardcode production secrets into Helm templates or `values.yaml`.

Provide configuration similar to:

```yaml
identity:
  jwt:
    issuer: identity-service
    accessTokenExpiration: 15m
    refreshTokenExpiration: 30d

  security:
    maxLoginAttempts: 5
    lockDuration: 15m

  password:
    minimumLength: 8
```

Sensitive values such as:

```text
database password
JWT private signing key
JWT key password
other credentials
```

must be supplied through Kubernetes Secrets or another production-safe secret mechanism.

Never commit real secrets.

## 4. PostgreSQL

The Identity Service requires PostgreSQL.

Do **not** automatically bundle a PostgreSQL StatefulSet into this application chart.

Treat PostgreSQL as an external dependency.

Support configuration such as:

```yaml
database:
  host: postgres
  port: 5432
  name: identity
  username: identity
```

The database password must come from a Kubernetes Secret.

Document that production PostgreSQL should normally be managed separately, for example through:

- managed PostgreSQL
- a dedicated PostgreSQL deployment
- a separate PostgreSQL Helm chart

Do not couple the Identity Service chart unnecessarily to PostgreSQL lifecycle.

## 5. JWT Signing Keys

Pay particular attention to JWT key handling.

The Identity Service uses **asymmetric JWT signing**.

The Helm deployment must allow the private signing key to be supplied securely through:

- Kubernetes Secret
- mounted Secret file
- or another production-safe mechanism

Do not generate a new JWT signing key every time the pod starts.

Do not store private keys in:

- Docker images
- ConfigMaps
- source code
- Git
- ordinary Helm values

Document how to create/install the Kubernetes Secret containing the JWT private signing key.

The Identity Service public key must remain available to consuming services using the public-key mechanism already implemented in previous phases.

## 6. Health Checks

Use Spring Boot Actuator health endpoints for Kubernetes probes.

Configure:

```text
startupProbe
readinessProbe
livenessProbe
```

appropriately.

Do not make liveness depend directly on PostgreSQL in a way that causes Kubernetes to restart a healthy application unnecessarily.

The application should distinguish:

```text
Liveness
    Application process is alive

Readiness
    Application is ready to receive traffic

Startup
    Application has finished starting
```

Make the probe paths configurable if appropriate.

## 7. Scaling

Provide optional HPA support.

Example:

```yaml
autoscaling:
  enabled: false
  minReplicas: 2
  maxReplicas: 5
  targetCPUUtilizationPercentage: 70
```

The application must be stateless from the Kubernetes pod's perspective.

Authentication/session state must remain in the database rather than local pod memory.

JWT access-token validation should remain stateless.

The service must therefore be safe to run with multiple replicas.

## 8. Cookie Support

The Identity Service has **first-class browser cookie authentication**.

The Helm configuration must support production cookie settings, including:

```yaml
cookies:
  secure: true
  httpOnly: true
  sameSite: Lax
  domain: ""
  path: /
```

Ensure that:

- access-token cookies are configured correctly
- refresh-token cookies are configured correctly
- cookies are not accidentally exposed to JavaScript
- Secure cookies work correctly over HTTPS
- SameSite behavior is configurable
- cookie domain/path are configurable
- cookie configuration works correctly behind Kubernetes Ingress
- cookie behavior is compatible with TLS termination/reverse proxies

Document considerations for deployments behind:

```text
Ingress
Load Balancer
TLS termination proxy
reverse proxy
```

## 9. Ingress

Provide an optional Ingress.

Example:

```yaml
ingress:
  enabled: false

  className: nginx

  host: identity.example.com

  tls:
    enabled: true
    secretName: identity-service-tls
```

Do not force users to expose the Identity Service publicly.

The default deployment should work as:

```text
ClusterIP
+
internal Kubernetes service
```

The Ingress must be disabled by default unless there is a strong reason otherwise.

Support:

- configurable ingress class
- hostname
- TLS
- annotations
- path
- pathType
- configurable TLS secret
- optional multiple hosts

## 10. Security

Apply Kubernetes security best practices.

The deployment should use:

```yaml
securityContext:
  runAsNonRoot: true
  allowPrivilegeEscalation: false
```

and where compatible:

- non-root UID/GID
- read-only root filesystem
- dropped capabilities
- `seccompProfile: RuntimeDefault`
- no privileged containers
- no host networking
- no host PID
- no host IPC

Use a minimal ServiceAccount.

Do not create unnecessary RBAC permissions.

Do not mount the Kubernetes ServiceAccount token unless the application actually requires it.

Secrets must never be stored in ConfigMaps.

Credentials must not be passed through container command-line arguments.

## 11. NetworkPolicy

Provide an optional NetworkPolicy.

It should support the intended communication model:

```text
Ingress / allowed clients
          ↓
   Identity Service
          ↓
      PostgreSQL
```

Avoid unnecessarily broad network access.

Make NetworkPolicy configurable because Kubernetes networking implementations differ.

Document that the exact ingress/egress selectors may need adjustment for the target cluster.

## 12. PodDisruptionBudget

Provide an optional PDB.

Example:

```yaml
pdb:
  enabled: false
  minAvailable: 1
```

Ensure it behaves correctly when replica count is 1.

Do not create a PDB configuration that makes a single-replica development deployment impossible to drain.

## 13. Environment-Specific Values

Provide:

```text
values.yaml
values-local.yaml
values-dev.yaml
values-staging.yaml
values-prod.yaml
```

Keep `values.yaml` safe and generic.

Never put real credentials or production secrets into any values file.

Demonstrate reasonable differences:

### Local

```text
1 replica
lower resource requirements
Ingress disabled
HPA disabled
PDB disabled
```

### Development

```text
1–2 replicas
development resources
optional Ingress
```

### Staging

```text
2 replicas
production-like configuration
TLS
optional HPA
```

### Production

```text
2+ replicas
HPA enabled
PDB enabled
TLS enabled
production resource limits
production security settings
```

These are example configurations, not universal production sizing recommendations.

## 14. Resources

Provide configurable resource requests and limits.

Example:

```yaml
resources:
  requests:
    cpu: 250m
    memory: 512Mi

  limits:
    cpu: "1"
    memory: 1Gi
```

Do not assume these values are universally correct.

Explain that resource values should be adjusted based on actual application metrics.

## 15. Scheduling

Support:

- nodeSelector
- tolerations
- affinity
- podAntiAffinity
- topologySpreadConstraints

Example configuration:

```yaml
nodeSelector: {}

tolerations: []

affinity: {}

topologySpreadConstraints: []
```

Do not force users into a specific cluster topology.

## 16. Graceful Shutdown

Configure Kubernetes and Spring Boot for graceful shutdown.

Support:

```yaml
terminationGracePeriodSeconds: 30
```

and ensure Spring Boot graceful shutdown is enabled/configured consistently.

Rolling updates should avoid unnecessary downtime.

Configure:

```yaml
strategy:
  type: RollingUpdate
```

with sensible rolling-update parameters.

## 17. ConfigMap

Use a ConfigMap for non-sensitive application configuration where appropriate.

Examples:

```text
Spring profile
JWT issuer
token expiration configuration
cookie configuration
security configuration
application behavior
```

Do not put:

```text
passwords
private keys
database secrets
JWT secrets
```

into the ConfigMap.

## 18. Secret

Provide a Secret template or a mechanism for referencing externally-created Secrets.

Prefer a design that allows production users to create Secrets independently.

For example:

```yaml
existingSecret: ""
```

so users can provide:

```bash
kubectl create secret generic identity-service-secrets \
  --from-literal=DB_PASSWORD='...' \
  --from-file=JWT_PRIVATE_KEY=./private-key.pem \
  -n identity
```

The chart should support using an existing Secret.

Do not require production credentials to be written into `values.yaml`.

## 19. Helm Tests

Add Helm tests where useful.

Provide a Kubernetes Job such as:

```text
templates/helm-test.yaml
```

that verifies the deployed Identity Service is reachable.

Use an image/tool that is actually available in a normal Kubernetes environment.

Document:

```bash
helm test identity-service -n identity
```

Do not make the Helm test depend on tools that are unlikely to exist in the test container.

## 20. Helm Validation

Document and validate:

```bash
helm lint deploy/helm/identity-service
```

Then:

```bash
helm template identity-service \
  deploy/helm/identity-service \
  -f deploy/helm/identity-service/values-dev.yaml
```

Then:

```bash
helm upgrade --install identity-service \
  deploy/helm/identity-service \
  -n identity \
  --create-namespace \
  -f deploy/helm/identity-service/values-dev.yaml
```

Also document:

```bash
kubectl get pods -n identity
kubectl get svc -n identity
kubectl get ingress -n identity
kubectl describe deployment -n identity
kubectl logs -n identity deployment/identity-service
```

## 21. Helm README

Create:

```text
deploy/helm/identity-service/README.md
```

Document:

1. Prerequisites
2. Chart structure
3. Configuration
4. Secrets
5. JWT signing-key setup
6. PostgreSQL configuration
7. Local installation
8. Development installation
9. Staging installation
10. Production installation
11. Ingress/TLS
12. Cookie configuration
13. Scaling
14. Health checks
15. NetworkPolicy
16. Pod security
17. Troubleshooting
18. Upgrade
19. Rollback
20. Uninstall
21. Helm tests

## 22. Upgrade and Rollback

Document:

```bash
helm upgrade ...
```

and:

```bash
helm rollback identity-service <REVISION> -n identity
```

Explain how database migrations behave during application upgrades.

The Helm chart must integrate safely with the Flyway migrations already implemented in the Identity Service.

Do not introduce a second independent database migration mechanism.

## 23. Application Configuration Integration

Do not invent a separate configuration architecture.

Reuse the configuration already established in Phases 1–7.

The Helm chart must integrate with:

```text
Spring Boot configuration
Spring profiles
PostgreSQL
Flyway
JWT configuration
JWT private key
cookie configuration
CORS
CSRF
Actuator
logging
security configuration
```

If any existing configuration needs to change slightly to support Kubernetes, update the application consistently rather than creating conflicting configuration systems.

## 24. Production Security Review

Before finishing Phase 8, perform a specific Helm/Kubernetes security and reliability review covering:

- secret exposure
- JWT private-key handling
- cookie security
- TLS
- Ingress configuration
- CORS
- CSRF
- pod security
- RBAC
- ServiceAccount permissions
- NetworkPolicy
- PostgreSQL connectivity
- readiness behavior
- liveness behavior
- startup behavior
- graceful shutdown
- rolling updates
- HPA
- PDB
- multiple replicas
- refresh-token concurrency
- statelessness
- database persistence
- configuration overrides
- resource limits
- production logging
- container security
- image security
- secret rotation considerations

## 25. Final Project Tree

At the end, show the complete final project tree including the Helm chart.

It should look approximately like:

```text
identity-service/
├── src/
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── .env.example
├── README.md
└── deploy/
    └── helm/
        └── identity-service/
            ├── Chart.yaml
            ├── values.yaml
            ├── values-local.yaml
            ├── values-dev.yaml
            ├── values-staging.yaml
            ├── values-prod.yaml
            ├── .helmignore
            ├── README.md
            └── templates/
                ├── _helpers.tpl
                ├── deployment.yaml
                ├── service.yaml
                ├── serviceaccount.yaml
                ├── configmap.yaml
                ├── secret.yaml
                ├── ingress.yaml
                ├── hpa.yaml
                ├── pdb.yaml
                ├── networkpolicy.yaml
                ├── helm-test.yaml
                └── NOTES.txt
```

## 26. Important Final Requirements

This is a real reusable production project.

Do not give me:

- pseudocode
- placeholders
- "implementation omitted"
- "add this yourself"
- incomplete YAML
- fake Kubernetes manifests
- fake Helm templates
- hardcoded production credentials
- hardcoded JWT private keys

Generate the **actual complete files**.

The Helm chart must be consistent with the actual Identity Service implementation from Phases 1–7.

If you discover an issue in the existing application that prevents correct Kubernetes deployment, fix the application as part of this phase and explain the change.

After generating everything:

1. Show the complete Helm chart.
2. Validate the Helm templates.
3. Check that all referenced values exist.
4. Check that all referenced Secrets/ConfigMaps are consistent.
5. Check that Kubernetes resource names are valid.
6. Check that the Deployment references the correct container port.
7. Check that probes point to valid Actuator endpoints.
8. Check that Ingress routes to the correct Service.
9. Check that HPA targets the correct Deployment.
10. Check that PDB behavior is safe for the configured replica count.
11. Check that NetworkPolicy does not accidentally block required traffic.
12. Check that JWT private keys are never exposed through ConfigMaps or ordinary values.
13. Check that cookie settings are appropriate for HTTPS/browser deployments.
14. Check that the application can run with multiple replicas.
15. Run the appropriate project and Helm validation/tests available in the environment.

Finally provide:

### Setup

Exact commands for:

```text
creating the namespace
creating required Secrets
installing the chart
checking deployment status
checking health
running Helm tests
upgrading
rolling back
uninstalling
```

### Production Notes

Explain the important production considerations for:

```text
PostgreSQL
JWT signing keys
TLS
Ingress
cookies
CSRF
CORS
secrets
replicas
HPA
PDB
NetworkPolicy
database migrations
backups
monitoring
logging
```

The final result should be a **complete reusable Kubernetes/Helm deployment package for the Identity Service**, not merely an example Helm chart.