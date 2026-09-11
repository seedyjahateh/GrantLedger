# GrantLedger

A Java 21 / Spring Boot research-account ledger with a minimal server-rendered interface, department-scoped OAuth/OIDC access, immutable expenses and reversals, and transactional budget enforcement.

GrantLedger is an **operational tracking ledger**. The university finance system remains authoritative. This repository supplies a pilot implementation; admitting real institutional data requires the gates in [pilot readiness](docs/pilot-readiness.md).

## Run locally

Prerequisites: Java 21, Docker with Compose v2, and Node.js 22+ for browser/contract checks. Maven is supplied through the checked-in wrapper. The application does not require Node.js at runtime.

```sh
docker compose up --build -d
```

Open <http://localhost:8080/grants>. Development-only accounts:

| Username | Password | Role |
|---|---|---|
| research-admin | local-admin-only | Research administrator |
| viewer | local-viewer-only | Read-only viewer |
| auditor | local-auditor-only | Read-only auditor |

Compose binds published ports to localhost. Its credentials and Keycloak realm are **synthetic development configuration**, unsuitable for institutional deployment. Login becomes available once Keycloak starts. Inspect `docker compose logs app identity` if it is still starting.

The database starts first, Flyway applies migrations under owner credentials, and a provisioning job grants the runtime role limited privileges and inserts two synthetic departments. Start by creating a draft grant, allocating its award, and activating it.

Stop with `docker compose stop`. Data persists in the named volume. No automatic reset or deletion command is part of normal startup.

## Run the Java process on the host

Start `db`, `migrate`, `provision`, and `identity` with Compose, then supply:

```sh
export DATABASE_PASSWORD=local-app-only
export OIDC_CLIENT_SECRET=local-web-only
export COOKIE_SECURE=false
./mvnw spring-boot:run
```

PowerShell:

```powershell
$env:DATABASE_PASSWORD = 'local-app-only'
$env:OIDC_CLIENT_SECRET = 'local-web-only'
$env:COOKIE_SECURE = 'false'
.\mvnw.cmd spring-boot:run
```

Use an installed JDK through `JAVA_HOME`. The ignored `.tools` directory, if present, is workstation-specific tooling and is not required by the project.

## Test and verify

```sh
./mvnw -B -ntp test
./mvnw -B -ntp verify
./mvnw -B -ntp spotless:check checkstyle:check spotbugs:check
npm ci
npm run contract:lint
npx playwright install chromium
npm run test:browser
```

`test` runs JUnit 5/Mockito, domain, architecture, and signed-JWT checks. `verify` additionally starts PostgreSQL through Testcontainers and runs the integration/concurrency/contract suite, then enforces JaCoCo coverage. Docker is required for the default integration path; a missing Docker daemon is a failure, not a silently skipped test.

Without Docker, point the tests at a **dedicated disposable PostgreSQL database**:

```powershell
$env:GL_TEST_DATABASE_URL = 'jdbc:postgresql://localhost:55432/grantledger_test'
$env:GL_TEST_DATABASE_USER = 'postgres'
$env:GL_TEST_DATABASE_PASSWORD = 'your-local-test-password'
.\mvnw.cmd verify
```

The test database user must own its schema. Tests insert synthetic data and temporarily install a test constraint to prove rollback. Never point these tests at an institutional or shared production database. See [verification](docs/verification.md) for measured results and remaining checks.

Reports: `target/surefire-reports`, `target/failsafe-reports`, `target/site/jacoco/index.html`, and `playwright-report`.

## API

The reviewed [OpenAPI contract](openapi/grantledger-v1.yaml) specifies 16 method/path operations under `/api/v1`. The file uses JSON syntax, which is valid YAML. Authenticated runtime docs: `/v3/api-docs/v1`; Swagger UI: `/swagger-ui/index.html`. Pilot Swagger mutation controls are disabled.

API calls require a bearer **access token** with audience `grantledger-api`, a lifetime of at most ten minutes, `roles`, and `department_ids`. Browser session cookies cannot authenticate API calls. Browser pages use OIDC and CSRF-protected server-side sessions.

```http
POST /api/v1/grants/{id}/entries
Authorization: Bearer <access-token>
Content-Type: application/json
Idempotency-Key: 4c23b27f-1242-4478-b79d-57aa6c885a4e

{
  "category": "TRAVEL",
  "amount": "125.40",
  "effectiveDate": "2026-09-01",
  "externalReference": "FINANCE-2026-0042",
  "description": "Conference registration"
}
```

Keep the same idempotency key and payload after a timeout. A replay returns the original 201 response and resource location. Reusing a key with different values returns 409. Authorization is rechecked before replay. Grant metadata, allocations, activation, and closure require the quoted `If-Match` value returned by the grant's `ETag`.

List defaults: zero-based `page=0`, `size=25`, maximum size 100. Each list has an allowlist of sort/filter fields. Errors use RFC 9457 problem JSON with a stable `code` and `traceId`.

## Rules that matter

- USD decimal strings with exactly two fractional digits; no floating-point money or silent rounding.
- Five fixed categories. Active allocations total the award and cannot fall below current expenditure.
- Remaining funds equal allocation minus expenses plus full reversals.
- All financial writes acquire the grant row lock before reading current state and balances.
- Entries and successful idempotency records commit with the audit event. Audit failure rolls back the transaction.
- Corrections create a full reversal once. Existing entries are never edited or deleted.
- Expense dates must be within the award and not in the future. Reversals use today's institutional date while the grant is active.
- Closure is permanent in v1 and requires reconciliation confirmation.
- CSV exports use a consistent snapshot, are capped at 10,000 entries, and neutralize spreadsheet formulas.

## Architecture and operations

- [Architecture, ER, and sequence diagrams](docs/architecture.md)
- [Architecture decisions](docs/adrs.md)
- [Requirements and acceptance criteria](docs/requirements.md)
- [Security and threat model](docs/security.md)
- [Configuration, deployment, and recovery](docs/operations.md)
- [Load testing](load/README.md)
- [Institutional pilot gates](docs/pilot-readiness.md)

One deployable Spring MVC service uses application services for both REST and HTML adapters. JPA owns transactional grant/entry persistence; parameterized JDBC handles aggregates, audit, and idempotency records. The database is the source of truth; there is no balance cache or message broker. Spring Session JDBC supports shared browser sessions across replicas.

## Development conventions

Constructor injection; records for DTOs; explicit entity mapping; domain policies without Spring dependencies; feature changes accompanied by behavioral tests. `spotless:apply` formats Java; use `spotless:check` in CI. Flyway migrations are immutable after release; Hibernate only validates the schema. Do not bind persistence entities directly to web requests or responses.

For Python/TypeScript engineers: `BigDecimal` replaces numeric money, `@Transactional` defines a database unit of work, Spring proxies mean self-invoked transactional methods do not open new boundaries, and JPA's persistence context can retain stale entities until refreshed. Read the posting and lock tests before changing transaction boundaries.

The GitHub workflows verify the build, contracts, security and containers. Publishing a release candidate is a protected manual workflow. Institutional deployment is intentionally gated on real infrastructure and ownership, not inferred from successful local tests.
