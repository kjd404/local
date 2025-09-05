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
public interface CsvReaderModule {
    @Provides
    @Singleton
    static MappingFileLocator mappingFileLocator(ObjectMapper mapper) {
        return new MappingFileLocator(mapper);
    }

    @Provides
    @Singleton
    @ElementsIntoSet
    static Set<TransactionCsvReader> csvReaders(MappingFileLocator locator) {
        try {
            return locator.locate().stream()
                    .map(ConfigurableCsvReader::new)
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load CSV mappings", e);
        }
    }
}
