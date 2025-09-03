package com.example.ingest;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CsvTransactionMapperTest {
    @Test
    void parsesCsv() throws Exception {
        String csv = "account_id,occurred_at,posted_at,amount_cents,currency,merchant,category,memo,source\n" +
                "1,2024-01-01T10:00:00Z,2024-01-02T10:00:00Z,-5678,USD,Store B,Refund,Returned item,capitalone\n";
        CsvTransactionMapper mapper = new CsvTransactionMapper();
        List<TransactionRecord> txs = mapper.parse(new StringReader(csv), Map.of());
        assertEquals(1, txs.size());
        TransactionRecord t = txs.get(0);
        assertEquals("1", t.accountId());
        assertEquals(-5678, t.amountCents());
        assertEquals("Store B", t.merchant());
        assertNotNull(t.hash());
    }

    @Test
    void parsesCapitalOneStyleCsv() throws Exception {
        String csv = "Transaction Date,Posted Date,Card No.,Description,Category,Debit,Credit\n" +
                "2025-04-30,2025-04-30,1828,CAPITAL ONE MOBILE PYMT,Payment/Credit,,600.00\n" +
                "2025-04-28,2025-04-30,1828,TST*ROYAL BAKEHOUSE,Dining,14.12,\n";
        CsvTransactionMapper mapper = new CsvTransactionMapper();
        List<TransactionRecord> txs = mapper.parse(new StringReader(csv), Map.of("source", "capitalone"));
        assertEquals(2, txs.size());
        TransactionRecord t0 = txs.get(0);
        assertEquals("1828", t0.accountId());
        assertEquals(60000, t0.amountCents());
        assertEquals(Instant.parse("2025-04-30T00:00:00Z"), t0.occurredAt());
        TransactionRecord t1 = txs.get(1);
        assertEquals(-1412, t1.amountCents());
        assertEquals("TST*ROYAL BAKEHOUSE", t1.merchant());
    }

    @Test
    void parsesOtherInstitutionCsv() throws Exception {
        String csv = "Transaction Date,Post Date,Description,Category,Type,Amount,Memo\n" +
                "04/30/2025,04/30/2025,Payment Thank You-Mobile,,Payment,18.62,\n" +
                "04/27/2025,04/29/2025,JetBrains Americas INC,Shopping,Sale,-18.62,\n";
        CsvTransactionMapper mapper = new CsvTransactionMapper();
        List<TransactionRecord> txs = mapper.parse(new StringReader(csv), Map.of("account_id", "2", "source", "otherbank"));
        assertEquals(2, txs.size());
        TransactionRecord t0 = txs.get(0);
        assertEquals("2", t0.accountId());
        assertEquals(1862, t0.amountCents());
        assertEquals("Payment", t0.type());
        TransactionRecord t1 = txs.get(1);
        assertEquals(-1862, t1.amountCents());
        assertEquals("JetBrains Americas INC", t1.merchant());
    }
}
