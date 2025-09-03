package org.artificers.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CapitalOneVentureXCsvReader extends BaseCsvReader implements TransactionCsvReader {
    @Override
    public String institution() { return "co"; }

    @Override
    public List<TransactionRecord> read(Path file, Reader reader, String accountId) {
        try (CSVReader csv = new CSVReader(reader)) {
            List<String[]> rows = csv.readAll();
            String[] header = Arrays.stream(rows.remove(0)).map(this::normalize).toArray(String[]::new);
            return rows.stream().map(r -> mapRow(accountId, header, r)).toList();
        } catch (IOException | CsvException e) {
            throw new RuntimeException(e);
        }
    }

    private TransactionRecord mapRow(String accountId, String[] header, String[] row) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < header.length && i < row.length; i++) {
            m.put(header[i], row[i]);
        }
        Instant occurredAt = parseDate(m.get("transaction_date"));
        Instant postedAt = parseDate(m.get("posted_date"));
        long amountCents = parseAmount(m.get("credit"), m.get("debit"));
        String currency = "USD";
        String merchant = m.get("description");
        String category = m.get("category");
        String type = null;
        String memo = null;
        String rawJson = new ObjectMapper().valueToTree(m).toString();
        String occurred = occurredAt == null ? "" : occurredAt.toString();
        String hash = DigestUtils.sha256Hex(accountId + amountCents + occurred + merchant);
        TransactionRecord tx = new CapitalOneVentureXTransaction(accountId, occurredAt, postedAt, amountCents,
                currency, merchant, category, type, memo, hash, rawJson);
        TransactionValidator.validate(tx);
        return tx;
    }
}
