package com.example.ingest;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CapitalOneVentureXCsvReaderTest {
    @Test
    void parsesCsv() {
        String csv = "Transaction Date,Posted Date,Card No.,Description,Category,Debit,Credit\n" +
                "2025-04-30,2025-04-30,1828,CAPITAL ONE MOBILE PYMT,Payment/Credit,,600.00\n" +
                "2025-04-28,2025-04-30,1828,TST*ROYAL BAKEHOUSE,Dining,14.12,\n";
        CapitalOneVentureXCsvReader reader = new CapitalOneVentureXCsvReader();
        List<TransactionRecord> txs = reader.read(null, new StringReader(csv), "1828");
        assertEquals(2, txs.size());
        TransactionRecord t0 = txs.get(0);
        assertEquals("1828", t0.accountId());
        assertEquals(60000, t0.amountCents());
        assertEquals(Instant.parse("2025-04-30T00:00:00Z"), t0.occurredAt());
        TransactionRecord t1 = txs.get(1);
        assertEquals(-1412, t1.amountCents());
        assertEquals("TST*ROYAL BAKEHOUSE", t1.merchant());
    }
}
