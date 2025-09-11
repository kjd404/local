Service-Scoped Flyway Migrations

Usage
- Set DB variables (prefer prefixed for multiple services):
  - SERVICE_TEMPLATE_DB_URL
  - SERVICE_TEMPLATE_DB_USER
  - SERVICE_TEMPLATE_DB_PASSWORD
  - Place them in repo-local `.env` if convenient (git-ignored).
- Run migrations:
  - `bazel run //ops/sql/service-template:db_migrate`

Notes
- This target uses a reusable Bazel macro (`//tools/sql:flyway.bzl`) and a common
  script that runs Flyway via Docker. It mounts this directory into the container
  and applies migrations to the specified database.
- If your DB URL references `localhost` or `127.0.0.1`, it is rewritten to
  `host.docker.internal` for the container to reach your host DB.
- To create your own service, copy this folder and update `env_prefix` in
  BUILD.bazel to a unique prefix.

