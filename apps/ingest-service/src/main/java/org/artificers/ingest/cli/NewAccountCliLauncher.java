package org.artificers.ingest.cli;

import com.zaxxer.hikari.HikariDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import picocli.CommandLine;

import java.nio.file.Path;

/** Launcher for {@link NewAccountCli} that wires dependencies from environment variables. */
public final class NewAccountCliLauncher {
    private NewAccountCliLauncher() {}

    public static void main(String[] args) throws Exception {
        String url = System.getenv("DB_URL");
        String user = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");
        Path configDir = Path.of(System.getenv().getOrDefault(
                "INGEST_CONFIG_DIR", System.getProperty("user.home") + "/.config/ingest"));

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(normalizeUrl(url));
        ds.setUsername(user);
        ds.setPassword(password);
        DSLContext dsl = DSL.using(ds, SQLDialect.POSTGRES);
        try {
            int code = new CommandLine(new NewAccountCli(dsl, configDir)).execute(args);
            System.exit(code);
        } finally {
            ds.close();
        }
    }

    private static String normalizeUrl(String url) {
        if (url == null) return null;
        if (url.startsWith("jdbc:")) return url;
        String normalized = url.replaceFirst("^postgres://", "postgresql://");
        return "jdbc:" + normalized;
    }
}
