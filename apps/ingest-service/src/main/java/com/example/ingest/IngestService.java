package com.example.ingest;

import org.jooq.DSLContext;
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
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(input, "*.csv")) {
            for (Path file : stream) {
                String shorthand = AccountResolver.extractShorthand(file);
                if (shorthand == null) continue;
                boolean ok = ingestFile(file, shorthand);
                Path targetDir = input.resolveSibling(ok ? "processed" : "failed");
                Files.createDirectories(targetDir);
                Files.move(file, targetDir.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public boolean ingestFile(Path file, String shorthand) {
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
                if (txs.isEmpty()) return false;
                ResolvedAccount account = accountResolver.resolve(shorthand);
                txs.forEach(t -> upsert(t, account.id()));
                return true;
            } catch (RuntimeException e) {
                log.debug("Reader {} failed for {}", reader.getClass().getSimpleName(), file, e);
            }
        } catch (IOException e) {
            log.error("Failed to ingest {}", file, e);
        } catch (IllegalArgumentException e) {
            log.error("Invalid shorthand {} for file {}", shorthand, file, e);
        }
        return false;
    }

    private void upsert(TransactionRecord t, long accountPk) {
        dsl.insertInto(DSL.table("transactions"))
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
                .set(DSL.field("raw_json"), DSL.field("?::jsonb", String.class, t.rawJson()))
                .onConflict(DSL.field("account_id", Long.class), DSL.field("hash"))
                .doNothing()
                .execute();
    }

    private Timestamp toTs(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }
}
