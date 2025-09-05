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
    @Provides
    @Singleton
    @ElementsIntoSet
    static Set<TransactionCsvReader> csvReaders(ObjectMapper mapper, TransactionValidator validator) {
        try {
            return new MappingFileLocator(mapper).locate().stream()
                    .map(m -> new ConfigurableCsvReader(mapper, validator, m))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load CSV mappings", e);
        }
    }
}

