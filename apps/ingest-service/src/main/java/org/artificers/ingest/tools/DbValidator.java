package org.artificers.ingest.tools;

import org.artificers.ingest.app.IngestApp;
import org.artificers.ingest.config.DbConfig;
import org.artificers.ingest.config.IngestConfig;
import org.artificers.ingest.di.DaggerIngestComponent;
import org.artificers.ingest.di.IngestComponent;
import org.artificers.jooq.tables.Transactions;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record2;
import org.jooq.impl.DSL;

import java.nio.file.Path;
import java.util.List;

/**
 * Validates the database state with summary queries using jOOQ.
 */
public final class DbValidator {
    public static void main(String[] args) throws Exception {
        String rawUrl = System.getenv("DB_URL");
        String user = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");

        // IngestConfig not used here beyond DI requirements
        Path ingestDir = Path.of(System.getenv().getOrDefault("INGEST_DIR", "storage/incoming"));
        Path configDir = Path.of(System.getenv().getOrDefault("INGEST_CONFIG_DIR",
                System.getProperty("user.home") + "/.config/ingest"));

        System.out.printf("Validating DB %s as %s%n", IngestApp.sanitize(rawUrl), user);

        DbConfig dbCfg = new DbConfig(rawUrl, user, password);
        IngestConfig cfg = new IngestConfig(ingestDir, configDir);
        IngestComponent component = DaggerIngestComponent.builder()
                .dbConfig(dbCfg)
                .ingestConfig(cfg)
                .build();
        try (var ignore = component.dataSourceCloseable()) {
            DSLContext ctx = component.dslContext();

            long accounts = ctx.fetchCount(DSL.table("accounts"));
            long tx = ctx.fetchCount(Transactions.TRANSACTIONS);
            long vw = ctx.fetchCount(DSL.table("transactions_view"));

            System.out.println("== Row counts ==");
            System.out.printf("accounts: %d%stransactions: %d%stransactions_view: %d%n",
                    accounts, System.lineSeparator(), tx, System.lineSeparator(), vw);

            Long total = ctx.select(DSL.coalesce(DSL.sum(Transactions.TRANSACTIONS.AMOUNT_CENTS), 0L))
                    .from(Transactions.TRANSACTIONS)
                    .fetchOne(0, Long.class);
            System.out.println("== Amount totals ==");
            System.out.printf("total_cents: %d%n", total);

            System.out.println("== Per-account counts ==");
            List<Record2<Long, Integer>> perAcct = ctx.select(Transactions.TRANSACTIONS.ACCOUNT_ID, DSL.count())
                    .from(Transactions.TRANSACTIONS)
                    .groupBy(Transactions.TRANSACTIONS.ACCOUNT_ID)
                    .orderBy(Transactions.TRANSACTIONS.ACCOUNT_ID)
                    .fetch();
            for (Record2<Long, Integer> r : perAcct) {
                System.out.printf("account_id=%d count=%d%n", r.value1(), r.value2());
            }

            System.out.println("== Duplicate (account_id, hash) check ==");
            var dups = ctx.select(Transactions.TRANSACTIONS.ACCOUNT_ID,
                            Transactions.TRANSACTIONS.HASH,
                            DSL.count().as("c"))
                    .from(Transactions.TRANSACTIONS)
                    .groupBy(Transactions.TRANSACTIONS.ACCOUNT_ID, Transactions.TRANSACTIONS.HASH)
                    .having(DSL.count().gt(1))
                    .fetch();
            if (dups.isEmpty()) {
                System.out.println("no duplicates");
            } else {
                dups.forEach(r -> System.out.printf("dup account_id=%s hash=%s c=%s%n",
                        r.value1(), r.value2(), r.value3()));
            }

            System.out.println("== Sample rows ==");
            ctx.select(Transactions.TRANSACTIONS.ID,
                            Transactions.TRANSACTIONS.ACCOUNT_ID,
                            Transactions.TRANSACTIONS.AMOUNT_CENTS,
                            Transactions.TRANSACTIONS.MERCHANT)
                    .from(Transactions.TRANSACTIONS)
                    .orderBy(Transactions.TRANSACTIONS.ID)
                    .limit(10)
                    .fetch()
                    .forEach(r -> System.out.printf("id=%s account_id=%s amount_cents=%s merchant=%s%n",
                            r.value1(), r.value2(), r.value3(), r.value4()));
        }
    }
}
