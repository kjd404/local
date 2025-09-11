package org.artificers.ingest.di;

import dagger.BindsInstance;
import dagger.Component;
import javax.inject.Singleton;
import java.io.Closeable;
import org.artificers.ingest.cli.NewAccountCli;
import org.artificers.ingest.service.IngestService;
import org.artificers.ingest.service.FileIngestionService;
import org.artificers.ingest.service.DirectoryWatchService;
import org.artificers.ingest.service.AccountShorthandParser;
import org.artificers.ingest.config.DbConfig;
import org.artificers.ingest.config.IngestConfig;
import org.jooq.DSLContext;

/** Dagger component assembling ingest services. */
@Singleton
@Component(modules = {DataModule.class, CsvReaderModule.class, ServiceModule.class})
public interface IngestComponent {
    IngestService ingestService();
    FileIngestionService fileIngestionService();
    DirectoryWatchService directoryWatchService();
    NewAccountCli newAccountCli();
    AccountShorthandParser accountShorthandParser();
    Closeable dataSourceCloseable();
    DSLContext dslContext();

    @Component.Builder
    interface Builder {
        @BindsInstance Builder dbConfig(DbConfig cfg);
        @BindsInstance Builder ingestConfig(IngestConfig cfg);
        IngestComponent build();
    }
}
