package org.artificers.ingest.service;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.artificers.ingest.csv.TransactionCsvReader;
import org.artificers.ingest.error.IngestException;
import org.artificers.ingest.error.TransactionIngestException;
import org.artificers.ingest.model.ResolvedAccount;
import org.artificers.ingest.model.TransactionRecord;

public class IngestService {
    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final DSLContext dsl;
    private final AccountResolver accountResolver;
    private final AccountShorthandParser shorthandParser;
    private final Map<String, TransactionCsvReader> readers;
    private final TransactionRepository repository;
    private final MaterializedViewRefresher viewRefresher;

    public IngestService(DSLContext dsl,
                         AccountResolver accountResolver,
                         AccountShorthandParser shorthandParser,
                         Set<TransactionCsvReader> readers,
                         TransactionRepository repository,
                         MaterializedViewRefresher viewRefresher) {
        this.dsl = dsl;
        this.accountResolver = accountResolver;
        this.shorthandParser = shorthandParser;
        this.readers = readers.stream().collect(Collectors.toMap(TransactionCsvReader::institution, r -> r));
        this.repository = repository;
        this.viewRefresher = viewRefresher;
    }

    public void ingestFile(Path file, String shorthand) throws IngestException, IOException {
        log.info("Ingesting file {} for shorthand {}", file, shorthand);
        AccountShorthandParser.ParsedShorthand ids;
        try {
            ids = shorthandParser.parse(shorthand);
        } catch (IllegalArgumentException e) {
            throw new IngestException("Invalid account shorthand " + shorthand, e);
        }
        List<TransactionRecord> txs = parseTransactions(file, ids);
        persistTransactions(shorthand, txs);
        refreshViews();
        log.info("Successfully ingested {} transactions from {}", txs.size(), file);
    }

    private List<TransactionRecord> parseTransactions(Path file,
                                                      AccountShorthandParser.ParsedShorthand ids)
            throws IOException, IngestException {
        TransactionCsvReader reader = readers.get(ids.institution());
        if (reader == null) {
            throw new IngestException("No reader for institution " + ids.institution());
        }
        String csv = Files.readString(file);
        try (Reader r = new StringReader(csv)) {
            List<TransactionRecord> txs = reader.read(file, r, ids.externalId());
            if (txs.isEmpty()) {
                throw new IngestException("No transactions found in " + file);
            }
            txs.forEach(t -> log.info("Read transaction: {}", t));
            return txs;
        }
    }

    private void persistTransactions(String shorthand, List<TransactionRecord> txs)
            throws IngestException {
        try {
            dsl.transaction(conf -> {
                DSLContext ctx = DSL.using(conf);
                ResolvedAccount account = accountResolver.resolve(ctx, shorthand);
                txs.forEach(t -> repository.upsert(ctx, t, account));
            });
        } catch (TransactionIngestException e) {
            throw new IngestException("Transaction ingest failed for " + e.record(), e);
        }
    }

    private void refreshViews() {
        viewRefresher.refreshTransactionsView();
    }

}
