# Security and data handling

## Trust boundaries

Untrusted clients enter through an institutional TLS proxy. Signed claims come from the configured OIDC issuer. The application trusts PostgreSQL for locking and persistence, and the institution for secret management, restricted networking, identity provisioning and database-administrator oversight.

The threat model covers OWASP Top 10:2025. Primary threats and controls:

| Threat | Control | Evidence |
|---|---|---|
| Cross-department object access | Role checks and department filtering before reads, counts and writes | Negative tests for nested paths and exports |
| Duplicate or racing financial requests | Grant lock, refreshed entity state, idempotency and unique constraints | Parallel requests and retry tests |
| Forged or misdirected token | RS256 signature, issuer, audience, time and maximum lifetime checks | Signed-token tests including forged signatures |
| CSRF and confused authentication | Separate stateless API chain; browser CSRF and server-side sessions | Cookie-only API rejection; missing-CSRF rejection |
| Mass assignment or query injection | DTO binding, strict scalar types, allowlisted fields/sorts, bound SQL parameters | Invalid-input tests |
| XSS or spreadsheet formula execution | Thymeleaf escaping, CSP and CSV neutralization | Rendering and formula tests |
| Audit deletion or partial commit | Restricted database role and transactional audit insertion | Permission and rollback tests |
| Resource exhaustion | Body/page/export limits, lock and SQL timeouts, bounded connection pools | Limit and contention tests |
| Vulnerable dependencies or leaked credentials | Maven BOM, lockfile, pinned action commits, CodeQL/SpotBugs/Trivy and Dependabot | CI artifacts and release review |
| Excessive telemetry disclosure | No body/token/cookie logging; generated request IDs and bounded metric labels | Log review |

## Identity contract

Claims: `iss`, `sub`, `roles` (array) and `department_ids` (UUID array). Role values are VIEWER, RESEARCH_ADMIN and AUDITOR. Missing claims grant no department access. The identity team provisions claims and access reviews; the service has no role-assignment endpoint.

API access tokens require audience `grantledger-api`, RS256, valid issuer and time bounds, a maximum ten-minute lifetime and at most 60 seconds clock skew. Browser sessions expire after ten minutes and require a fresh authorization round trip. Verify role removal behavior with the real institutional provider before pilot use. MFA is enforced at the provider.

Do not enable resource-owner-password grants in the institutional provider. The local Keycloak realm enables that flow only for synthetic load-test tooling. Production test clients use approved client credentials or delegated access tokens.

## Data classification and retention

Collect grant references, financial amounts and non-personal descriptions only. Do not store student identifiers, payroll, patient, banking or payment-card data. A PERSONNEL budget category is an aggregate classification, not permission to store individual compensation. Free-text fields still require training and incident procedures.

FERPA applicability depends on the actual records and institutional processing. No code-level claim of FERPA compliance is made. The privacy/data owner must approve classification, permitted use, disclosure recording, retention, legal holds and disposition. No automated purge is enabled in v1. Idempotency responses contain the original entry DTO and share the ledger's approved retention requirements.

Every committed business mutation has an audit event. Financial entries and audit rows are append-only for the runtime role. Export initiation is durable before returning the generated CSV; request completion/failure is operational telemetry. These records do not replace institution-specific FERPA disclosure records.

## Reporting a vulnerability

Do not include real grant records, tokens or credentials in public issues. Contact the designated institutional security owner through the approved private reporting channel. That owner and channel must be recorded before pilot launch.
