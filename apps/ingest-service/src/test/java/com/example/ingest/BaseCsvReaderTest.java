package com.example.ingest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BaseCsvReaderTest {
    static class Stub extends BaseCsvReader {
        long amt(String amount) { return parseAmount(amount); }
        long amt(String credit, String debit) { return parseAmount(credit, debit); }
    }

    @Test
    void parsesAmountsWithThousandsSeparators() {
        Stub s = new Stub();
        assertEquals(123456, s.amt("1,234.56"));
        assertEquals(123456, s.amt("1,234.56", null));
        assertEquals(-123456, s.amt(null, "1,234.56"));
    }
}
