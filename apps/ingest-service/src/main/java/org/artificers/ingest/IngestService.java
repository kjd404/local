package org.artificers.ingest;

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class IngestService {
    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final DSLContext dsl;
    private final AccountResolver accountResolver;
    private final Map<String, TransactionCsvReader> readers;

    public IngestService(DSLContext dsl, AccountResolver accountResolver, List<TransactionCsvReader> readers) {
        this.dsl = dsl;
        this.accountResolver = accountResolver;
        this.readers = readers.stream().collect(Collectors.toMap(TransactionCsvReader::institution, r -> r));
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
                        txs.forEach(t -> upsert(ctx, t, account.id()));
                    });
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

    private void upsert(DSLContext ctx, TransactionRecord t, long accountPk) {
        try {
            ctx.insertInto(DSL.table("transactions"))
                    .set(DSL.field("account_id"), accountPk)
                    .set(DSL.field("occurred_at"), toTs(t.occurredAt()))
                    .set(DSL.field("posted_at"), toTs(t.postedAt()))
                    .set(DSL.field("amount_cents"), t.amountCents())
                    .set(DSL.field("currency"), t.currency())
                    .set(DSL.field("merchant"), t.merchant())
                    .set(DSL.field("category"), t.category())
                    .set(DSL.field("txn_type"), t.type())
                    .set(DSL.field("memo"), t.memo())
                    .set(DSL.field("hash"), t.hash())
                    .set(DSL.field("raw_json", String.class), t.rawJson())
                    .onConflict(DSL.field("account_id", Long.class), DSL.field("hash"))
                    .doNothing()
                    .execute();
        } catch (DataAccessException e) {
            throw new TransactionIngestException(t, e);
        }
    }

    private Timestamp toTs(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }
}
