# Load test

Use a dedicated synthetic database. `seed.sql` creates 1,000 grants and 1,000,000 ledger entries (900,000 expenses, 100,000 reversals). It intentionally creates synthetic history directly and must not run on a pilot database. Run it once on an empty test dataset; a second run fails unique constraints rather than silently duplicating history.

```sh
psql "$SYNTHETIC_DATABASE_URL" -v ON_ERROR_STOP=1 -v synthetic_database_confirmed=true -f load/seed.sql
```

Copy the emitted list of 20 grant IDs to `GRANT_IDS`. Configure an approved synthetic test identity with access to the seeded department. The script refreshes tokens; do not use a short-lived static token for a 20-minute benchmark.

```sh
k6 run -e BASE_URL=http://localhost:8080 -e GRANT_IDS="$GRANT_IDS" \
  --summary-export=artifacts/load-summary.json load/reference.js
```

Default run: five-minute warmup followed by fifteen measured minutes at 50 requests/sec, with 80% reads, 15% writes and 5% reports. Writes distribute across at least 20 grants. Historical seed entries are older than the report window so mixed-workload reports remain small. Keep the institutional effective date consistent through `EFFECTIVE_DATE`.

For an approved service account set TOKEN_URL, CLIENT_ID, CLIENT_SECRET and CLIENT_CREDENTIALS=true through protected environment injection. Local password-grant defaults are development-only.

Record OS, CPU/memory limits, database memory, storage, network placement, exact commit, image digest and raw results. Reference targets assume app 2 vCPU/2 GiB and database 2 vCPU/4 GiB with SSD storage, with the generator on another host. Do not apply that label to an unconstrained workstation.

Also run a 30-minute soak and an independent 10,000-row CSV test, and capture p95 by operation rather than only overall latency. Hot-grant race correctness is tested in JUnit; its latency is not the distributed-write threshold. Expected conflicts belong in a separate contention scenario, not in the successful-workload error budget.
