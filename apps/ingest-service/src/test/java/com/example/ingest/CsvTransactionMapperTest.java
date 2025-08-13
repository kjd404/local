package com.example.ingest;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvTransactionMapperTest {
    @Test
    void parsesCsv() throws Exception {
        String csv = "account_id,occurred_at,posted_at,amount_cents,currency,merchant,category,memo,source\n" +
                "acct1,2024-01-01T10:00:00Z,2024-01-02T10:00:00Z,-5678,USD,Store B,Refund,Returned item,capitalone\n";
        CsvTransactionMapper mapper = new CsvTransactionMapper();
        List<Transaction> txs = mapper.parse(new StringReader(csv));
        assertEquals(1, txs.size());
        Transaction t = txs.get(0);
        assertEquals("acct1", t.accountId);
        assertEquals(-5678, t.amountCents);
        assertEquals("Store B", t.merchant);
        assertNotNull(t.hash);
    }
}
