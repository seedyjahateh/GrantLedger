# Architecture decision records

All decisions are accepted for v1 unless the institutional support gate requires revision. Revisit a decision only with a concrete requirement or measured failure.

| ID | Context and decision | Alternatives and consequences | Verification |
|---|---|---|---|
| 001 | A solo implementation needs clear boundaries: one service/database, layered Java packages | Multiple services add distributed consistency and deployment work; deferred | ArchUnit and runnable Compose stack |
| 002 | Use Java 21, Spring Boot 3.5 and JUnit 5 with pinned dependencies | Boot 4 changes the test ecosystem; institutional maintenance support must be approved | Compile, tests, dependency review |
| 003 | Corrections must preserve history: immutable positive expenses and full reversals | Mutable expense rows erase history; double-entry accounting exceeds scope | Unique reversal constraint and reconciliation tests |
| 004 | Reject concurrent overspending: lock and refresh the grant before every mutation | Optimistic retries and serializable transactions increase retry complexity | Concurrent posting, closure and reallocation tests |
| 005 | Exact money across Java, SQL and clients: decimal strings, BigDecimal, NUMERIC | Binary floats and implicit rounding rejected | Boundary/coercion tests |
| 006 | Browser and API are separate security contexts: OIDC sessions and JWT bearer tokens | One cookie-authenticated API would require different CSRF semantics | API cookie rejection and browser CSRF tests |
| 007 | Access equals institutional roles plus departments | Application-managed accounts duplicate identity governance | Scope tests on nested resources and reports |
| 008 | Network retries must not duplicate expenses: persistent scoped idempotency and source-reference uniqueness | Short-lived cache keys cannot establish durable replay | Concurrent retry and changed-payload tests |
| 009 | Provide a usable pilot interface: Thymeleaf calls shared application services | SPA adds a second build and authentication surface | Browser task script and service tests |
| 010 | Schema changes are reviewed and repeatable: Flyway plus separate runtime role | Hibernate schema mutation is unsuitable for controlled releases | Migration and privilege checks |
| 011 | Reports show current allocations and dated activity | Historical-budget reconstruction requires effective-dated budget events; deferred | Snapshot exports and current balance assertions |
| 012 | GrantLedger is a limited operational pilot | Official accounting ownership needs integration, approvals and additional controls | Product labeling and pilot checklist |

Runtime audit and idempotency rows use parameterized JDBC within the same Spring transaction as JPA. This avoids persisting arbitrary response graphs as entities while retaining database atomicity. Aggregate queries avoid materializing entire ledgers.
