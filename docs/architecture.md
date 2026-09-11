# Architecture

```mermaid
flowchart LR
  browser["Administrator browser"] --> proxy["TLS proxy"]
  client["Bearer-token API client"] --> proxy
  proxy --> web["Spring MVC REST and Thymeleaf adapters"]
  browser --> identity["Institutional OIDC provider"]
  web --> application["Authorized application services"]
  application --> policy["Java domain policies"]
  application --> persistence["JPA and parameterized JDBC"]
  persistence --> database[("PostgreSQL")]
  web --> identity
  application --> telemetry["Sanitized logs and metrics"]
  migration["Flyway release job"] --> database
```

The API chain uses stateless JWT authentication; the browser chain uses OAuth login, sessions and CSRF. Session cookies never grant REST access. Roles and department claims resolve into an immutable actor at the adapter boundary. Application services enforce access before data retrieval or writes.

```mermaid
erDiagram
  department ||--o{ grantRecord : owns
  grantRecord ||--|{ budgetAllocation : allocates
  grantRecord ||--o{ ledgerEntry : records
  budgetAllocation ||--o{ ledgerEntry : categorizes
  ledgerEntry o|--o| ledgerEntry : reverses
  grantRecord ||--o{ auditEvent : documents
  grantRecord ||--o{ idempotencyRecord : deduplicates
```

All business IDs are UUIDs. Monetary columns use NUMERIC(19,2); business amounts are capped at 999999999.99. Dates use DATE; timestamps use TIMESTAMPTZ. Composite foreign keys prevent cross-grant budget and reversal links. Partial unique indexes prevent duplicate expense references and multiple reversals.

```mermaid
sequenceDiagram
  participant caller as Caller
  participant service as Posting service
  participant database as PostgreSQL
  caller->>service: Expense and idempotency key
  service->>database: Check visibility and lock grant
  service->>database: Refresh current grant state
  service->>database: Find prior scoped key
  alt Existing matching request
    database-->>service: Original response
    service-->>caller: Replay 201
  else New request
    service->>database: Read allocations and net expenditure
    service->>service: Check active state, dates and available budget
    service->>database: Insert entry, audit and key; update grant version
    service->>database: Commit together
    service-->>caller: 201 and Location
  end
```

Grant-level pessimistic locks serialize mutations using READ COMMITTED. Refreshing after lock acquisition prevents stale first-level-cache state. Queries and exports use REPEATABLE READ snapshots. No transaction spans interactive sign-in or an HTTP response stream.

```mermaid
sequenceDiagram
  participant browser as Browser
  participant app as GrantLedger
  participant idp as Identity provider
  participant db as PostgreSQL
  browser->>app: Protected page
  app-->>browser: OIDC authorization redirect
  browser->>idp: Authenticate
  idp-->>browser: Authorization code
  browser->>app: Callback
  app->>idp: Exchange and validate tokens
  app->>db: Store session
  app-->>browser: Secure session cookie
  browser->>app: Form and CSRF token
  app->>app: Validate session age, role, department and CSRF
  app-->>browser: Result or redirect
```

The initial institutional topology is one application replica behind a TLS proxy, PostgreSQL on a private network, and internal monitoring. Session storage and database locks permit two replicas without sticky routing. Infrastructure must restrict port 9090; management endpoints are not business-authenticated because monitoring is a separate network trust boundary.
