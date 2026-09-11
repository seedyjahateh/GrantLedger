# Configuration and operations

## Configuration

Use `.env.example` as the inventory, not as a production secret file. Inject credentials from the institution's secret store. Configure separate owner and runtime logins. Set `RUN_MIGRATIONS=false` on serving replicas.

| Variable | Purpose |
|---|---|
| DATABASE_URL / USER / PASSWORD | JDBC endpoint and restricted application login |
| MIGRATION_USER / PASSWORD | Used only for explicit migration runs |
| OIDC_ISSUER / AUDIENCE | Trusted issuer and API audience |
| OIDC_CLIENT_ID / CLIENT_SECRET | Browser authorization-code client |
| OIDC_AUTH_URI / TOKEN_URI / JWK_URI / USERINFO_URI | Explicit endpoints; avoid a discovery dependency at startup |
| OIDC_ROLES_CLAIM / DEPARTMENTS_CLAIM | Defaults to roles / department_ids |
| INSTITUTION_TIMEZONE | Effective-date boundary; development default America/New_York |
| COOKIE_SECURE | True in institutional environments |
| MANAGEMENT_ADDRESS | Loopback by default; expose only to monitoring on private networks |

The proxy enforces TLS, a 64 KiB request limit, request timeouts and institution-specific rate limits. Do not forward arbitrary untrusted forwarding headers. Configure trusted proxy handling at deployment when the external HTTPS host differs from application addressing. Register the exact external OIDC callback. Deny direct client access to port 9090.

## Release

1. Confirm the pilot-readiness gates and the tested commit's CI evidence.
2. Back up the database and verify free space.
3. Run Flyway `validate` then `migrate` once with schema-owner credentials.
4. Apply `ops/runtime-grants.sql` as owner. The runtime role must already exist.
5. Start the approved image **by digest**, supplying secrets externally. Use a non-root container, read-only root filesystem, temporary `/tmp`, 2 GiB limit and a 30-second shutdown allowance.
6. Verify readiness, sign-in, a scoped read, a synthetic posting/reversal and audit visibility.
7. Observe errors, database connections and lock timeouts before opening pilot traffic.

Never deploy the development Compose realm or its known credentials. Never run Hibernate schema creation/update in the pilot. Published migration files are immutable.

## Rollback

Use expand/contract migrations. Roll back the application image only after verifying compatibility with the current schema. Otherwise deploy a forward fix. Do not restore a database backup as routine application rollback: that can discard accepted financial transactions. A recovery restore requires explicit incident coordination and reconciliation of the recovery window.

## Backup and recovery

The institutional backup service encrypts daily backups and protects them with the approved retention/access policy. For an isolated recovery exercise, restore a backup into a newly provisioned database, run Flyway validation, compare entry counts, and independently compute:

```sql
SELECT b.grant_id, b.category, b.allocated_amount,
       COALESCE(SUM(CASE WHEN e.type = 'EXPENSE' THEN e.amount ELSE -e.amount END), 0) AS net_expenditure
FROM budget_allocation b
LEFT JOIN ledger_entry e ON e.grant_id = b.grant_id AND e.budget_allocation_id = b.id
GROUP BY b.grant_id, b.category, b.allocated_amount;
```

Compare with the pre-backup snapshot and API balances. Record backup timestamp, recovery start/end, missing-transaction window and operator approval. Targets: RPO ≤24 hours, RTO ≤4 hours. Never test restore against the live database.

## Monitoring and incidents

Internal endpoints: `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/prometheus`. Liveness does not depend on PostgreSQL; readiness does. Import `ops/alerts.yml` into the institutional Prometheus stack and supply alert routing. Backup failures and readiness HTTP checks also require platform-owned alerts.

Logs contain generated request IDs, route templates, statuses and duration. Metrics have bounded labels; user/grant IDs are not metric dimensions. Never enable HTTP-body, Hibernate bind-value or token logging with real data.

For lock conflicts, retry a posting with the same key; investigate sustained hot-grant contention. For 503/database failures, check connectivity/pool saturation and reconcile ambiguous posting results by retrying the original key. For a failed audit write, verify schema permissions and database health; the financial operation should have rolled back. For identity outages, existing valid API tokens can use cached signing keys, while browser sign-in may fail.

Two replicas require shared PostgreSQL sessions and the same security configuration. Allocate ten database connections per replica and budget PostgreSQL capacity before increasing replica count. Test login continuity and concurrent posting against both replicas before enabling them for the pilot.
