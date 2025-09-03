package com.example.ingest;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class IngestService {
    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final DSLContext dsl;
    private final AccountResolver accountResolver;
    private final CsvTransactionMapper mapper;

    public IngestService(DSLContext dsl, AccountResolver accountResolver) {
        this.dsl = dsl;
        this.accountResolver = accountResolver;
        this.mapper = new CsvTransactionMapper();
    }

    public void scanAndIngest(Path input) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(input, "*.csv")) {
            for (Path file : stream) {
                boolean ok = ingestFile(file);
                Path targetDir = input.resolveSibling(ok ? "processed" : "failed");
                Files.createDirectories(targetDir);
                Files.move(file, targetDir.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public boolean ingestFile(Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            List<TransactionRecord> txs = mapper.parse(file, reader);
            ResolvedAccount account = accountResolver.resolve(txs, file);
            txs.forEach(t -> upsert(t, account.id(), account.institution()));
            return true;
        } catch (IOException | com.opencsv.exceptions.CsvException e) {
            log.error("Failed to ingest {}", file, e);
            return false;
        }
    }

    private void upsert(TransactionRecord t, long accountPk, String source) {
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
                .set(DSL.field("source"), source)
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
