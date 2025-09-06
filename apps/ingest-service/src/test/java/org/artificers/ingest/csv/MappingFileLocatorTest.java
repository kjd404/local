package org.artificers.ingest.csv;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.artificers.ingest.csv.MappingFileLocator;
import org.artificers.ingest.csv.ConfigurableCsvReader;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MappingFileLocatorTest {
    @Test
    void locatesJsonMappings() throws Exception {
        MappingFileLocator locator = new MappingFileLocator(new ObjectMapper());
        List<ConfigurableCsvReader.Mapping> mappings = locator.locate();
        assertTrue(mappings.stream().anyMatch(m -> "co".equals(m.institution())));
        assertTrue(mappings.stream().anyMatch(m -> "ch".equals(m.institution())));
    }
}
