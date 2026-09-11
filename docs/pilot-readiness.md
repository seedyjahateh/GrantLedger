# Institutional pilot readiness

This is a go-live gate, not a declaration that the deployment is approved. Repository implementation and local verification cannot supply institutional ownership or real infrastructure.

| Gate | Required evidence | Status |
|---|---|---|
| Platform owner | Named owner, Linux host, TLS proxy, network boundaries and resource limits | Institution required |
| Identity owner | Issuer, client registration, claim mapping, MFA and access-removal test | Institution required |
| Database owner | PostgreSQL service, owner/runtime credentials, TLS, backup schedule | Institution required |
| Spring support | Approved maintenance/support path for Spring Boot 3.5 | Institution required |
| Data/privacy owner | Permitted data, FERPA assessment, retention/disclosure/legal-hold procedure | Institution required |
| Security review | Threat-model review and disposition of high/critical findings | Review required |
| Operations | Support hours, escalation, dashboards and tested alerts | Institution required |
| Recovery | Isolated restore meeting RPO 24 hours and RTO 4 hours, exact balance reconciliation | Exercise required |
| Performance | Recorded reference hardware, million-row workload and per-operation percentiles | See verification record |
| Administrator UAT | Three users, 15-minute introduction, at least 90% task completion | Cohort required |
| Release | Tagged commit, tested image digest, SBOM, provenance and migration evidence | Release workflow required |

Initial cohort: 3–5 administrators, 1–2 auditors, at most 25 active grants. Weekly reconciliation against official finance records is required. Closure is permanent in v1. The pilot availability objective is 99.5% during agreed service hours; it can only be measured after launch.

UAT tasks: sign in, create a grant, allocate/activate, post an expense, explain an overrun rejection, reverse a mistake, export a date range, inspect the audit trail, and close a reconciled grant. Capture completion time, interventions, discrepancies and accessibility issues using synthetic data first.
