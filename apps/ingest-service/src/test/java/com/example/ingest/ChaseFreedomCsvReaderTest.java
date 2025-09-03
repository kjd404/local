package com.example.ingest;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChaseFreedomCsvReaderTest {
    @Test
    void parsesCsv() {
        String csv = "Transaction Date,Post Date,Description,Category,Type,Amount,Memo\n" +
                "04/30/2025,04/30/2025,Payment Thank You-Mobile,,Payment,18.62,\n" +
                "04/27/2025,04/29/2025,JetBrains Americas INC,Shopping,Sale,-18.62,\n";
        ChaseFreedomCsvReader reader = new ChaseFreedomCsvReader();
        List<TransactionRecord> txs = reader.read(null, new StringReader(csv), "1111");
        assertEquals(2, txs.size());
        TransactionRecord t0 = txs.get(0);
        assertEquals("1111", t0.accountId());
        assertEquals(1862, t0.amountCents());
        assertEquals("Payment", t0.type());
        TransactionRecord t1 = txs.get(1);
        assertEquals(-1862, t1.amountCents());
        assertEquals("JetBrains Americas INC", t1.merchant());
    }
}
