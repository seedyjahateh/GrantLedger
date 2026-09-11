# Requirements and delivery traceability

## 1. Overview & Problem

Replace inconsistent spreadsheet tracking with a department-scoped operational research ledger. The official university finance system remains authoritative. Delivery is a six-week solo build with a working ledger by week four.

## 2. Goals & Non-Goals

Goals: exact monetary accounting, concurrency safety, attributable corrections, a usable administrator interface, a documented API and reviewable Java engineering. Excludes effort reporting, payroll, payments, ERP integration, double-entry accounting, partial reversals, attachments and multi-currency.

## 3. Personas

Hiring manager: evaluate delivery, Java fluency and test discipline. Research administrator: accurate posting, understandable balances and recoverable errors. Architect: assess boundaries, authorization, transactions, data integrity and operability.

## 4. User Stories

Create/allocate/activate a grant; post within funds; reject overruns; safely retry; reverse an expense once; reallocate above expenditure; read scoped balances; export dated activity; inspect audit; permanently close after reconciliation.

## 5. Functional Requirements

FR-01: unique normalized award number and validated grant metadata. FR-02: DRAFT → ACTIVE → CLOSED only. FR-03: five allocations; active totals equal the award. FR-04: USD exact decimal money. FR-05: dated expenses with unique references. FR-06: full immutable reversals. FR-07: scoped durable idempotency. FR-08: grant locking and atomic audit. FR-09: scoped lists, current balances and bounded CSV. FR-10: CSRF-protected administrator pages.

## 6. Non-Functional Requirements

Reference workload: 1,000 grants/1,000,000 entries, 50 requests/sec, 80% reads/15% writes/5% reports. p95: detail 200 ms, list/balance 300 ms, writes 400 ms, HTML 500 ms, 10,000-row CSV 3 seconds. Unexpected errors <0.1%. Report actual hardware and results; do not infer these from passing unit tests.

## 7. Architecture & Diagrams

See architecture.md: one deployable service, layered adapters/application/domain/persistence, PostgreSQL, external OIDC, institutional telemetry. JPA transactions and grant locks are shared by REST and HTML.

## 8. Data Model & Migrations

Department, Grant, BudgetAllocation, LedgerEntry, AuditEvent, IdempotencyRecord and JDBC sessions. UUID identifiers, NUMERIC money, UTC timestamps and date-only effective dates. Flyway exclusively owns schema changes; runtime financial history is append-only.

## 9. API Design (OpenAPI)

The reviewed contract defines 16 versioned operations, string money, error codes, role/scope requirements, pagination, idempotency and ETags. Independent response validation supplements runtime operation-catalog comparison.

## 10. Standards, Security & Compliance

Constructor injection, explicit DTO mapping, independent policies and ArchUnit boundaries. Separate OAuth API and OIDC browser chains. OWASP threat model, restricted financial-data handling, institutional FERPA assessment, durable audit and secure supply-chain checks.

## 11. Testing & CI/CD

JUnit 5/Mockito, real PostgreSQL/Testcontainers, MockMvc, signed JWT tests, contract schema validation, Playwright and k6. JaCoCo goals: overall 80% line/70% branch; policy 90% line/85% branch. CI includes formatting, Checkstyle, SpotBugs, CodeQL, dependency/image/secret scanning and SBOM generation.

## 12. ADRs

See adrs.md for accepted architecture, money, concurrency, identity, idempotency, UI, migration and operational-boundary decisions.

## 13. Delivery Plan

Week 1: foundation and named institutional owners. Week 2: grants/budgets/read API. Week 3: immutable ledger and concurrency proof. Week 4: reports and working UI. Week 5: security/operations/performance hardening. Week 6: restore, administrator UAT and gated release. Institutional delays delay the pilot rather than relaxing its gates.

## 14. Success Metrics

All 16 operations documented/tested; exact-cent reconciliation; no duplicate financial effect in retry tests; no cross-department disclosures in negative tests; every committed mutation audited; required coverage and benchmark targets; ≥90% administrator UAT completion; recorded recovery exercise.

## 15. Risks & Assumptions

One institution, USD, fixed categories, direct posting, configured institutional timezone, no automatic purge, no official-system writeback. Principal risks: Java learning, late institutional provisioning, maintenance support, hot-grant contention, privacy in free text and unavailable operational ownership.

## 16. README Outline

The README supplies quick start, configuration, API examples, domain rules, verification commands, architecture links, operations and contributor guidance. The verification record distinguishes implemented tests from evidence actually collected.
