package com.example.ingest;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CapitalOneVentureXCsvReaderTest {
    @Test
    void parsesDatesAmountsAndUtf8() {
        String csv = "Transaction Date,Posted Date,Card No.,Description,Category,Debit,Credit\n" +
                "2025-04-30,2025-04-30,1828,CAPITAL ONE MOBILE PYMT,Payment/Credit,,600.00\n" +
                "04/29/2025,04/30/2025,1828,Café Técnico,Travel,7.50,\n" +
                "2025-04-28T00:00:00Z,2025-04-28T00:00:00Z,1828,Book Store,Dining,14.12,\n";
        CapitalOneVentureXCsvReader reader = new CapitalOneVentureXCsvReader();
        List<TransactionRecord> txs = reader.read(null, new StringReader(csv), "1828");
        assertEquals(3, txs.size());
        TransactionRecord t0 = txs.get(0);
        assertEquals(60000, t0.amountCents());
        assertEquals(Instant.parse("2025-04-30T00:00:00Z"), t0.occurredAt());
        TransactionRecord t1 = txs.get(1);
        assertEquals(-750, t1.amountCents());
        assertEquals("Café Técnico", t1.merchant());
        assertEquals(Instant.parse("2025-04-29T00:00:00Z"), t1.occurredAt());
        TransactionRecord t2 = txs.get(2);
        assertEquals(-1412, t2.amountCents());
        assertEquals(Instant.parse("2025-04-28T00:00:00Z"), t2.occurredAt());
    }
}
