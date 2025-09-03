package org.artificers.ingest;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChaseFreedomCsvReaderTest {
    @Test
    void parsesDatesAmountsAndUtf8() {
        String csv = "Transaction Date,Posted Date,Description,Amount\n" +
                "2025-04-30T00:00:00Z,2025-04-30T00:00:00Z,Salary,1000.00\n" +
                "2025-05-01,2025-05-02,Coffee Shop,-12.34\n" +
                "05/03/2025,05/03/2025,Café Bücher,-20.00\n";
        ChaseFreedomCsvReader reader = new ChaseFreedomCsvReader();
        List<TransactionRecord> txs = reader.read(null, new StringReader(csv), "1234");
        assertEquals(3, txs.size());
        TransactionRecord t0 = txs.get(0);
        assertEquals(100000, t0.amountCents());
        assertEquals(Instant.parse("2025-04-30T00:00:00Z"), t0.occurredAt());
        TransactionRecord t1 = txs.get(1);
        assertEquals(-1234, t1.amountCents());
        assertEquals(Instant.parse("2025-05-01T00:00:00Z"), t1.occurredAt());
        TransactionRecord t2 = txs.get(2);
        assertEquals(-2000, t2.amountCents());
        assertEquals("Café Bücher", t2.merchant());
        assertEquals(Instant.parse("2025-05-03T00:00:00Z"), t2.occurredAt());
    }
}
