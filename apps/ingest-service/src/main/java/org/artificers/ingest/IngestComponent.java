package org.artificers.ingest;

import dagger.BindsInstance;
import dagger.Component;
import javax.inject.Singleton;
import org.artificers.ingest.cli.NewAccountCli;

/** Dagger component assembling ingest services. */
@Singleton
@Component(modules = {DataModule.class, CsvReaderModule.class, ServiceModule.class})
public interface IngestComponent {
    IngestService ingestService();
    FileIngestionService fileIngestionService();
    DirectoryWatchService directoryWatchService();
    NewAccountCli newAccountCli();
    AccountShorthandParser accountShorthandParser();

    @Component.Builder
    interface Builder {
        @BindsInstance Builder dbConfig(DbConfig cfg);
        @BindsInstance Builder ingestConfig(IngestConfig cfg);
        IngestComponent build();
    }
}
