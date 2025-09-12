package org.artificers.ingest.csv;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class MappingFileLocatorTest {
  @Test
  void locatesJsonMappings() throws Exception {
    MappingFileLocator locator = new MappingFileLocator(new ObjectMapper());
    List<ConfigurableCsvReader.Mapping> mappings = locator.locate();
    assertTrue(mappings.stream().anyMatch(m -> "co".equals(m.institution())));
    assertTrue(mappings.stream().anyMatch(m -> "ch".equals(m.institution())));
  }
}
