package org.artificers.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import dagger.Module;
import dagger.Provides;
import java.util.Set;
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
    static AccountResolver accountResolver(DSLContext dsl) {
        return new AccountResolver(dsl);
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
                                       Set<TransactionCsvReader> readers,
                                       TransactionRepository repo,
                                       MaterializedViewRefresher refresher) {
        return new IngestService(dsl, resolver, readers, repo, refresher);
    }

    @Provides
    @Singleton
    static FileIngestionService fileIngestionService(IngestService service) {
        return new FileIngestionService(service);
    }

    @Provides
    @Singleton
    static DirectoryWatchService directoryWatchService(IngestService service, IngestConfig cfg) {
        return new DirectoryWatchService(service, cfg.ingestDir());
    }

    @Provides
    @Singleton
    static NewAccountCli newAccountCli(DSLContext dsl, IngestConfig cfg) {
        return new NewAccountCli(dsl, cfg.configDir());
    }
}
