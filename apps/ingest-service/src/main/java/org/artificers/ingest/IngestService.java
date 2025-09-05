package org.artificers.ingest;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    public boolean ingestFile(Path file, String shorthand) {
        log.info("Ingesting file {} for shorthand {}", file, shorthand);
        try {
            AccountShorthandParser.ParsedShorthand ids = shorthandParser.parse(shorthand);
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
                        txs.forEach(t -> repository.upsert(ctx, t, account));
                    });
                    viewRefresher.refreshTransactionsView();
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

}
