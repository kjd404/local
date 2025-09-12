package org.artificers.ingest.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.file.Path;
import org.artificers.ingest.csv.ConfigurableCsvReader;
import org.junit.jupiter.api.Test;

class AccountShorthandParserTest {
  @Test
  void parsesUsingSampleMapping() throws Exception {
    try (InputStream in = getClass().getResourceAsStream("/mappings/co.json")) {
      ConfigurableCsvReader.Mapping mapping =
          new ObjectMapper().readValue(in, ConfigurableCsvReader.Mapping.class);
      String shorthand = mapping.institution() + "1828";
      AccountShorthandParser parser = new AccountShorthandParser();
      AccountShorthandParser.ParsedShorthand ids = parser.parse(shorthand);
      assertEquals(mapping.institution(), ids.institution());
      assertEquals("1828", ids.externalId());
    }
  }

  @Test
  void extractsFromCsvFilename() {
    AccountShorthandParser parser = new AccountShorthandParser();
    assertEquals("ch1234", parser.extract(Path.of("ch1234.csv")));
    assertNull(parser.extract(Path.of("note.txt")));
  }

  @Test
  void rejectsInvalidStrings() {
    AccountShorthandParser parser = new AccountShorthandParser();
    assertThrows(IllegalArgumentException.class, () -> parser.parse("bad"));
    assertThrows(IllegalArgumentException.class, () -> parser.parse(null));
  }
}
