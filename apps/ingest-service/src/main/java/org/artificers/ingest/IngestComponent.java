package org.artificers.ingest;

import dagger.Component;
import javax.inject.Singleton;
import org.artificers.ingest.cli.NewAccountCli;

/** Dagger component assembling ingest services. */
@Singleton
@Component(modules = {DataModule.class, CsvReaderModule.class, ServiceModule.class})
public interface IngestComponent {
    IngestService ingestService();
    DirectoryWatchService directoryWatchService();
    NewAccountCli newAccountCli();
}
