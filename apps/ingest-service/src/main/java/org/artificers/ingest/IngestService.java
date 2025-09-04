package org.artificers.ingest;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class IngestService {
    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final DSLContext dsl;
    private final AccountResolver accountResolver;
    private final Map<String, TransactionCsvReader> readers;
    private final Map<String, String> tables;

    public IngestService(DSLContext dsl, AccountResolver accountResolver, List<TransactionCsvReader> readers) {
        this.dsl = dsl;
        this.accountResolver = accountResolver;
        this.readers = readers.stream().collect(Collectors.toMap(TransactionCsvReader::institution, r -> r));
        this.tables = Map.of(
                "ch", "chase_transactions",
                "co", "capital_one_transactions");
    }

    public void scanAndIngest(Path input) throws IOException {
        log.info("Scanning directory {}", input.toAbsolutePath());
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(input, "*.csv")) {
            for (Path file : stream) {
                log.info("Found file {}", file);
                String shorthand = AccountResolver.extractShorthand(file);
                if (shorthand == null) {
                    log.warn("Skipping file {} with unrecognized name", file);
                    continue;
                }
                boolean ok = ingestFile(file, shorthand);
                log.info("Ingestion {} for file {}", ok ? "succeeded" : "failed", file);
                Path targetDir = input.resolveSibling(ok ? "processed" : "error");
                Files.createDirectories(targetDir);
                Files.move(file, targetDir.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public boolean ingestFile(Path file, String shorthand) {
        log.info("Ingesting file {} for shorthand {}", file, shorthand);
        try {
            AccountResolver.ParsedShorthand ids = AccountResolver.parse(shorthand);
            TransactionCsvReader reader = readers.get(ids.institution());
            if (reader == null) {
                log.error("No reader for institution {}", ids.institution());
                return false;
            }
            String csv = Files.readString(file);
            try (Reader r = new StringReader(csv)) {
                List<TransactionRecord> txs = reader.read(file, r, ids.externalId());
                if (txs.isEmpty()) {
                    log.warn("No transactions found in {}", file);
                    return false;
                }
                txs.forEach(t -> log.info("Read transaction: {}", t));
                try {
                    dsl.transaction(conf -> {
                        DSLContext ctx = DSL.using(conf);
                        ResolvedAccount account = accountResolver.resolve(ctx, shorthand);
                        txs.forEach(t -> upsert(ctx, t, account));
                    });
                    refreshMaterializedView();
                } catch (TransactionIngestException e) {
                    log.error("Transaction ingest failed for {}", e.record(), e);
                    return false;
                }
                log.info("Successfully ingested {} transactions from {}", txs.size(), file);
                return true;
            }
        } catch (IllegalArgumentException e) {
            log.error("Invalid shorthand {} for file {}", shorthand, file, e);
        } catch (Exception e) {
            log.error("Failed to ingest {}", file, e);
        }
        return false;
    }

    private void upsert(DSLContext ctx, TransactionRecord t, ResolvedAccount account) {
        String table = tables.get(account.institution());
        if (table == null) {
            throw new TransactionIngestException(t, new IllegalArgumentException("Unknown institution " + account.institution()));
        }
        try {
            ctx.insertInto(DSL.table(DSL.name(table)))
                    .set(DSL.field(DSL.name(table, "account_id"), Long.class), account.id())
                    .set(DSL.field(DSL.name(table, "occurred_at"), OffsetDateTime.class), toOffsetDateTime(t.occurredAt()))
                    .set(DSL.field(DSL.name(table, "posted_at"), OffsetDateTime.class), toOffsetDateTime(t.postedAt()))
                    .set(DSL.field(DSL.name(table, "amount_cents"), Long.class), t.amountCents())
                    .set(DSL.field(DSL.name(table, "currency"), String.class), t.currency())
                    .set(DSL.field(DSL.name(table, "merchant"), String.class), t.merchant())
                    .set(DSL.field(DSL.name(table, "category"), String.class), t.category())
                    .set(DSL.field(DSL.name(table, "txn_type"), String.class), t.type())
                    .set(DSL.field(DSL.name(table, "memo"), String.class), t.memo())
                    .set(DSL.field(DSL.name(table, "hash"), String.class), t.hash())
                    .set(DSL.field(DSL.name(table, "raw_json"), JSONB.class), JSONB.valueOf(t.rawJson()))
                    .onConflict(
                            DSL.field(DSL.name(table, "account_id"), Long.class),
                            DSL.field(DSL.name(table, "hash"), String.class)
                    )
                    .doNothing()
                    .execute();
            ctx.insertInto(DSL.table(DSL.name("transactions")))
                    .set(DSL.field(DSL.name("transactions", "account_id"), Long.class), account.id())
                    .set(DSL.field(DSL.name("transactions", "occurred_at"), OffsetDateTime.class), toOffsetDateTime(t.occurredAt()))
                    .set(DSL.field(DSL.name("transactions", "posted_at"), OffsetDateTime.class), toOffsetDateTime(t.postedAt()))
                    .set(DSL.field(DSL.name("transactions", "amount_cents"), Long.class), t.amountCents())
                    .set(DSL.field(DSL.name("transactions", "currency"), String.class), t.currency())
                    .set(DSL.field(DSL.name("transactions", "merchant"), String.class), t.merchant())
                    .set(DSL.field(DSL.name("transactions", "category"), String.class), t.category())
                    .set(DSL.field(DSL.name("transactions", "txn_type"), String.class), t.type())
                    .set(DSL.field(DSL.name("transactions", "memo"), String.class), t.memo())
                    .set(DSL.field(DSL.name("transactions", "hash"), String.class), t.hash())
                    .set(DSL.field(DSL.name("transactions", "raw_json"), JSONB.class), JSONB.valueOf(t.rawJson()))
                    .onConflict(
                            DSL.field(DSL.name("transactions", "account_id"), Long.class),
                            DSL.field(DSL.name("transactions", "hash"), String.class)
                    )
                    .doNothing()
                    .execute();
        } catch (DataAccessException e) {
            throw new TransactionIngestException(t, e);
        }
    }

    private OffsetDateTime toOffsetDateTime(Instant i) {
        return i == null ? null : OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    private void refreshMaterializedView() {
        try {
            dsl.execute("REFRESH MATERIALIZED VIEW transactions_view");
        } catch (DataAccessException e) {
            log.debug("Skipping materialized view refresh: {}", e.getMessage());
        }
    }
}
