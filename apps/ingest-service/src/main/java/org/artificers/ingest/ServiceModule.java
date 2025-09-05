package org.artificers.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import dagger.Module;
import dagger.Provides;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchService;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Singleton;
import org.artificers.ingest.cli.NewAccountCli;
import org.jooq.DSLContext;

/** Module providing core services. */
@Module
public interface ServiceModule {
    @Provides
    @Singleton
    static ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Provides
    @Singleton
    static TransactionValidator transactionValidator() {
        return new BasicTransactionValidator();
    }

    @Provides
    @Singleton
    static AccountShorthandParser accountShorthandParser() {
        return new AccountShorthandParser();
    }

    @Provides
    @Singleton
    static AccountResolver accountResolver(DSLContext dsl, AccountShorthandParser parser) {
        return new AccountResolver(dsl, parser);
    }

    @Provides
    @Singleton
    static TransactionRepository transactionRepository() {
        return new TransactionRepository();
    }

    @Provides
    @Singleton
    static MaterializedViewRefresher materializedViewRefresher(DSLContext dsl) {
        return new MaterializedViewRefresher(dsl);
    }

    @Provides
    @Singleton
    static IngestService ingestService(DSLContext dsl,
                                       AccountResolver resolver,
                                       AccountShorthandParser parser,
                                       Set<TransactionCsvReader> readers,
                                       TransactionRepository repo,
                                       MaterializedViewRefresher refresher) {
        return new IngestService(dsl, resolver, parser, readers, repo, refresher);
    }

    @Provides
    @Singleton
    static FileIngestionService fileIngestionService(IngestService service,
                                                     AccountShorthandParser parser) {
        return new FileIngestionService(service, parser);
    }

    @Provides
    @Singleton
    static ExecutorService directoryWatchExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("directory-watch");
            return t;
        });
    }

    @Provides
    @Singleton
    static WatchService watchService() {
        try {
            return FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Provides
    @Singleton
    static DirectoryWatchService directoryWatchService(FileIngestionService fileService,
                                                       IngestConfig cfg,
                                                       ExecutorService executor,
                                                       WatchService watchService,
                                                       AccountShorthandParser parser) {
        return new DirectoryWatchService(fileService, cfg.ingestDir(), executor, watchService, parser);
    }

    @Provides
    @Singleton
    static NewAccountCli newAccountCli(DSLContext dsl, IngestConfig cfg) {
        return new NewAccountCli(dsl, cfg.configDir());
    }
}
