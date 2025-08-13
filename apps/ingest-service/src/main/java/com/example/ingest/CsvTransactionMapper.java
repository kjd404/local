package com.example.ingest;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.IOException;
import java.io.Reader;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CsvTransactionMapper {
    public List<Transaction> parse(Reader reader) throws IOException, CsvException {
        try (CSVReader csv = new CSVReader(reader)) {
            List<String[]> rows = csv.readAll();
            String[] header = rows.remove(0);
            return rows.stream().map(r -> mapRow(header, r)).toList();
        }
    }

    private Transaction mapRow(String[] header, String[] row) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < header.length && i < row.length; i++) {
            m.put(header[i], row[i]);
        }
        Transaction t = new Transaction();
        t.accountId = m.get("account_id");
        t.occurredAt = parseInstant(m.get("occurred_at"));
        t.postedAt = parseInstant(m.get("posted_at"));
        t.amountCents = Long.parseLong(m.get("amount_cents"));
        t.currency = m.getOrDefault("currency", "USD");
        t.merchant = m.get("merchant");
        t.category = m.get("category");
        t.memo = m.get("memo");
        t.source = m.get("source");
        t.rawJson = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(m).toString();
        t.hash = DigestUtils.sha256Hex(t.accountId + t.amountCents + t.occurredAt + t.merchant);
        return t;
    }

    private Instant parseInstant(String v) {
        return v == null || v.isBlank() ? null : Instant.parse(v);
    }
}
