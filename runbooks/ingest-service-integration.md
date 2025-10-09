# Ingest Service Integration Runbook

## Purpose & Expected Results
- Exercise the ingest-service end-to-end using the sample CSV `ch1234-example.csv`.
- Confirm two transactions land in the canonical `transactions` table with the expected merchant names and cent amounts.
- Tear down the temporary database artifacts created for the test run.

Expected rows (ordered by `occurred_at`):
| occurred_at        | account_id | merchant                 | amount_cents | posted_at         | txn_type |
|--------------------|------------|--------------------------|--------------|-------------------|----------|
| 2025-04-27T00:00Z  | 1          | JetBrains Americas INC   |        -1862 | 2025-04-29T00:00Z | Sale     |
| 2025-04-30T00:00Z  | 1          | Payment Thank You-Mobile |         1862 | 2025-04-30T00:00Z | Payment  |

After executing the teardown step, the `ingest` database and its volumes should be removed.

## Prerequisites
- `.env` file populated with database credentials (`DB_URL`, `DB_USER`, `DB_PASSWORD`). Copy `cp .env-sample .env` if you have not customized credentials.
- Docker Desktop (or compatible runtime) running locally.
- Bazel installed and available on `PATH`.
- Choose a host port for the runbook database. Default to `15432` to avoid clobbering production; override only if that port is unavailable.

## Execution Steps
1. In the shell you will use for the runbook, export the database environment variables and start PostgreSQL (stick with `15432` unless you have a conflict):
   ```bash
   export INGEST_DB_PORT=${INGEST_DB_PORT:-15432}
   export DB_USER=ingest
   export DB_PASSWORD=ingest
   export DB_NAME=ingest
   docker compose up -d
   ```
2. Configure connection variables for Bazel commands. Either maintain exports in this shell or create a throwaway `.env` (e.g., `cp .env-sample .env.runbook`). Make sure the port matches `INGEST_DB_PORT`:
   ```bash
   export DB_URL=jdbc:postgresql://localhost:${INGEST_DB_PORT:-15432}/ingest
   ```
3. Apply database migrations:
   ```bash
   bazel run //ops/sql:db_migrate
   ```
4. Ingest the sample CSV (use an absolute path when invoking Bazel so the runfiles sandbox can locate it):
   ```bash
   bazel run //apps/ingest-service:ingest_app -- --file=$PWD/apps/ingest-service/src/test/resources/examples/ch1234-example.csv
   ```
5. Verify the ingested rows and key metadata:
   ```bash
   docker compose exec db psql -U ingest -d ingest -c "SELECT occurred_at, account_id, merchant, amount_cents, posted_at, txn_type FROM transactions ORDER BY occurred_at;"
   ```
   The results should match the table above: JetBrains first (Sale, -1862 cents) followed by the payment (Payment, +1862 cents), both for `account_id=1` with the expected timestamps.
6. Optional validation helpers:
   ```bash
   docker compose exec db psql -U ingest -d ingest -c "SELECT COUNT(*) FROM transactions;"
   bazel run //apps/ingest-service:db_validate
   ```
7. Tear down the environment and drop volumes:
   ```bash
   docker compose down -v
   ```

## Notes
- If you changed database credentials, export `DB_URL`, `DB_USER`, and `DB_PASSWORD` to match before running Bazel targets.
- Re-running the ingestion without dropping volumes will append data; use the validation queries to confirm the expected row count.
- Avoid overwriting long-lived `.env` files that contain production credentials—use shell exports or a temporary env file instead.
- `docker compose` loads `.env` automatically; exported variables in your shell take precedence during the runbook session.
- The runbook defaults to host port `15432`; choose a different `INGEST_DB_PORT` only if that port is already in use.
- If you prefer to reuse an existing Postgres service instead of the runbook container, skip steps 1 and 7, point the credentials at a throwaway database/schema, and avoid `docker compose down -v` so production data stays intact.
