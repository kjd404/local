# Teller Poller

## Configuration

### Tokens and Certificates
- Set `TELLER_TOKENS` with a comma-separated list of Teller API tokens.
- Set `TELLER_CERT_FILE` and `TELLER_KEY_FILE` to paths of the PEM-encoded client certificate and private key for mTLS.
- These environment variables are consumed by `make deploy`.

## Local Development with Tilt
1. Export `TELLER_TOKENS`, `TELLER_CERT_FILE`, and `TELLER_KEY_FILE`.
2. Build images:
   ```bash
   make build-app
   ```
3. Launch the stack:
   ```bash
   make tilt
   ```
4. Access the service at [http://localhost:8080](http://localhost:8080).

## Backfill
- On startup the poller backfills any account whose `backfilled_at` field is `NULL`.
- To trigger a fresh backfill for an account, clear its `backfilled_at` in Postgres and restart the teller-poller pod (e.g. `kubectl rollout restart deployment/teller-poller`).

## Operations Runbook

### Health and Metrics
- Health check: `curl http://teller-poller:8080/actuator/health` should return `{"status":"UP"}`.
- Metrics are exposed via the Actuator endpoint. Useful metrics:
  - `account.poll.success`
  - `account.poll.failure`
  - `account.poll.last_success.epoch_ms`
  Fetch using `curl http://teller-poller:8080/actuator/metrics/<metric-name>`.

### Troubleshooting Failed Accounts
- Inspect logs for `account_poll_failed` or `account_poll_retry` messages which include the account ID and attempt count.
- Check `account_poll_state` for stuck cursors and ensure the corresponding account has valid tokens.
- Verify `TELLER_TOKENS`, `TELLER_CERT_FILE`, and `TELLER_KEY_FILE` are correctly configured and not expired.
