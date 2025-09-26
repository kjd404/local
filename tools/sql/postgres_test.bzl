def postgres_pytest(
        name,
        *,
        sql_dir,
        env_prefix = "",
        history_table = None,
        pytest_args = None,
        deps = None,
        data = None,
        tags = None,
        size = None,
        visibility = None):
    """Defines a py_test that runs pytest against a Flyway-initialized Postgres container."""

    args = ["--sql-dir=%s" % sql_dir]
    if env_prefix:
        args.append("--env-prefix=%s" % env_prefix)
    if history_table:
        args.append("--history-table=%s" % history_table)
    if pytest_args:
        args.extend(pytest_args)

    if data == None:
        data = []
    data = data + ["//tools/sql:flyway_migrate.sh"]
    if deps == None:
        deps = []
    if tags == None:
        tags = []
    tags = tags + ["requires-docker", "no-sandbox"]

    native.py_test(
        name = name,
        srcs = ["//tools/sql:postgres_test_runner.py"],
        main = "//tools/sql:postgres_test_runner.py",
        args = args,
        data = data,
        deps = ["//tools/sql:postgres_test_runner_lib"] + deps,
        tags = tags,
        size = size,
        visibility = visibility,
        local = True,
    )
