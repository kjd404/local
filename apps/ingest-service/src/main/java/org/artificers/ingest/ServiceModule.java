package org.artificers.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import dagger.Module;
import dagger.Provides;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    static IngestService ingestService(DSLContext dsl, AccountResolver resolver, Set<TransactionCsvReader> readers) {
        return new IngestService(dsl, resolver, readers.stream().toList());
    }

    @Provides
    @Singleton
    static DirectoryWatchService directoryWatchService(IngestService service) {
        String dir = System.getenv().getOrDefault("INGEST_DIR", "storage/incoming");
        return new DirectoryWatchService(service, dir);
    }

    @Provides
    @Singleton
    static NewAccountCli newAccountCli(DSLContext dsl) {
        Path configDir = Paths.get(System.getenv().getOrDefault("INGEST_CONFIG_DIR",
                System.getProperty("user.home") + "/.config/ingest"));
        return new NewAccountCli(dsl, configDir);
    }
}
