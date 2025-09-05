package org.artificers.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.StringReader;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurableCsvReaderTest {
    private ConfigurableCsvReader reader(String name, ObjectMapper mapper) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/mappings/" + name + ".json")) {
            ConfigurableCsvReader.Mapping mapping = new ObjectMapper().readValue(in, ConfigurableCsvReader.Mapping.class);
            return new ConfigurableCsvReader(mapper, mapping);
        }
    }

    @Test
    void parsesChaseFile() throws Exception {
        String csv = "Transaction Date,Post Date,Description,Category,Type,Amount,Memo\n" +
                "04/30/2025,04/30/2025,Payment Thank You-Mobile,,Payment,18.62,\n" +
                "04/27/2025,04/29/2025,JetBrains Americas INC,Shopping,Sale,-18.62,\n";
        ConfigurableCsvReader reader = reader("ch", new ObjectMapper());
        List<TransactionRecord> txs = reader.read(null, new StringReader(csv), "1234");
        assertEquals(2, txs.size());
        TransactionRecord t0 = txs.get(0);
        assertEquals(1862, t0.amountCents());
        assertEquals("Payment Thank You-Mobile", t0.merchant());
        assertEquals("Payment", t0.type());
        assertEquals(Instant.parse("2025-04-30T00:00:00Z"), t0.occurredAt());
        TransactionRecord t1 = txs.get(1);
        assertEquals(-1862, t1.amountCents());
        assertEquals("JetBrains Americas INC", t1.merchant());
        assertEquals("Sale", t1.type());
        assertEquals("Shopping", t1.category());
        assertEquals(Instant.parse("2025-04-27T00:00:00Z"), t1.occurredAt());
    }

    @Test
    void parsesCapitalOneFile() throws Exception {
        String csv = "Transaction Date,Posted Date,Card No.,Description,Category,Debit,Credit\n" +
                "2025-04-30,2025-04-30,1828,CAPITAL ONE MOBILE PYMT,Payment/Credit,,600.00\n" +
                "2025-04-28,2025-04-30,1828,TST*ROYAL BAKEHOUSE,Dining,14.12,\n";
        ConfigurableCsvReader reader = reader("co", new ObjectMapper());
        List<TransactionRecord> txs = reader.read(null, new StringReader(csv), "1828");
        assertEquals(2, txs.size());
        TransactionRecord t0 = txs.get(0);
        assertEquals(60000, t0.amountCents());
        assertEquals("CAPITAL ONE MOBILE PYMT", t0.merchant());
        assertEquals(Instant.parse("2025-04-30T00:00:00Z"), t0.occurredAt());
        assertTrue(t0.rawJson().contains("\"card_no\":\"1828\""));
        TransactionRecord t1 = txs.get(1);
        assertEquals(-1412, t1.amountCents());
        assertEquals("TST*ROYAL BAKEHOUSE", t1.merchant());
        assertEquals("Dining", t1.category());
        assertEquals(Instant.parse("2025-04-28T00:00:00Z"), t1.occurredAt());
        assertTrue(t1.rawJson().contains("\"card_no\":\"1828\""));
    }

    @Test
    void parsesIntAmounts() throws Exception {
        String mapping = "{" +
                "\"institution\":\"xx\"," +
                "\"fields\":{" +
                "\"date\":{\"target\":\"occurred_at\",\"type\":\"timestamp\"}," +
                "\"debit\":{\"target\":\"amount_cents\",\"type\":\"int\"}," +
                "\"credit\":{\"target\":\"amount_cents\",\"type\":\"int\"}" +
                "}}";
        ConfigurableCsvReader.Mapping m = new ObjectMapper().readValue(mapping, ConfigurableCsvReader.Mapping.class);
        ConfigurableCsvReader reader = new ConfigurableCsvReader(new ObjectMapper(), m);
        String csv = "date,debit,credit\n" +
                "2025-04-30,100,0\n" +
                "2025-04-29,0,200\n";
        List<TransactionRecord> txs = reader.read(null, new StringReader(csv), "1");
        assertEquals(2, txs.size());
        assertEquals(-100, txs.get(0).amountCents());
        assertEquals(200, txs.get(1).amountCents());
    }

    @Test
    void usesInjectedMapper() throws Exception {
        class CountingMapper extends ObjectMapper {
            int calls = 0;
            @Override
            public com.fasterxml.jackson.databind.JsonNode valueToTree(Object fromValue) {
                calls++;
                return super.valueToTree(fromValue);
            }
        }
        CountingMapper mapper = new CountingMapper();
        ConfigurableCsvReader reader = reader("ch", mapper);
        String csv = "Transaction Date,Post Date,Description,Category,Type,Amount,Memo\n" +
                "04/30/2025,04/30/2025,Payment Thank You-Mobile,,Payment,18.62,\n";
        reader.read(null, new StringReader(csv), "1234");
        assertTrue(mapper.calls > 0);
    }
}
