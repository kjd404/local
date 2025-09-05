package org.artificers.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Singleton;

/** Module for CSV reader configuration. */
@Module
public final class CsvReaderModule {
    private final Set<TransactionCsvReader> readers;

    public CsvReaderModule() {
        this(new ObjectMapper());
    }

    CsvReaderModule(ObjectMapper mapper) {
        try {
            this.readers = new MappingFileLocator(mapper).locate().stream()
                    .map(m -> new ConfigurableCsvReader(mapper, m))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load CSV mappings", e);
        }
    }

    @Provides
    @Singleton
    @ElementsIntoSet
    Set<TransactionCsvReader> csvReaders() {
        return readers;
    }
}
