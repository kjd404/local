package org.artificers.ingest.csv;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BaseCsvReaderTest {
  static class Stub extends BaseCsvReader {
    long amt(String amount) {
      return parseAmount(amount);
    }

    long amt(String credit, String debit) {
      return parseAmount(credit, debit);
    }

    java.time.Instant date(String v) {
      return parseDate(v);
    }
  }

  @Test
  void parsesAmountsWithThousandsSeparators() {
    Stub s = new Stub();
    assertEquals(123456, s.amt("1,234.56"));
    assertEquals(123456, s.amt("1,234.56", null));
    assertEquals(-123456, s.amt(null, "1,234.56"));
  }

  @Test
  void parsesDatesWithoutLeadingZeros() {
    Stub s = new Stub();
    java.time.Instant expected =
        java.time.LocalDate.of(2025, 5, 3).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
    assertEquals(expected, s.date("5/3/2025"));
  }
}
