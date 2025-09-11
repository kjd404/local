def flyway_migration(name, sql_dir = None, env_prefix = None):
    """
    Defines a Bazel `sh_binary` target to run Flyway migrations from a given directory.

    Args:
      name: name of the runnable target (e.g., "db_migrate").
      sql_dir: workspace-relative path to the migrations directory.
               Defaults to the current package path.
      env_prefix: optional prefix for DB env vars (e.g., "SERVICEA" uses SERVICEA_DB_URL, ...).
    """
    if sql_dir == None:
        sql_dir = native.package_name()

    args = [
        "--sql-dir=%s" % sql_dir,
    ]
    if env_prefix:
        args.append("--env-prefix=%s" % env_prefix)

    runner = name + "_runner.sh"
    native.genrule(
        name = name + "_prepare",
        srcs = ["//tools/sql:flyway_migrate.sh"],
        outs = [runner],
        cmd = "cp $(location //tools/sql:flyway_migrate.sh) $@ && chmod +x $@",
        visibility = ["//visibility:private"],
    )

    native.sh_binary(
        name = name,
        srcs = [runner],
        args = args,
        # Include SQL files so changes are tracked in runfiles.
        data = native.glob(["*.sql"]),
        visibility = ["//visibility:public"],
    )
