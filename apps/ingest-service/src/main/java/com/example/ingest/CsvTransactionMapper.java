package com.example.ingest;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CsvTransactionMapper {

    public List<TransactionRecord> parse(Reader reader) throws IOException, CsvException {
        return parse(null, reader, Map.of());
    }

    public List<TransactionRecord> parse(Reader reader, Map<String, String> defaults) throws IOException, CsvException {
        return parse(null, reader, defaults);
    }

    public List<TransactionRecord> parse(Path file, Reader reader) throws IOException, CsvException {
        return parse(file, reader, Map.of());
    }

    public List<TransactionRecord> parse(Path file, Reader reader, Map<String, String> defaults) throws IOException, CsvException {
        try (CSVReader csv = new CSVReader(reader)) {
            List<String[]> rows = csv.readAll();
            String[] rawHeader = rows.remove(0);
            String[] header = Arrays.stream(rawHeader).map(this::normalize).toArray(String[]::new);
            return rows.stream().map(r -> mapRow(header, r, defaults)).toList();
        }
    }

    private TransactionRecord mapRow(String[] header, String[] row, Map<String, String> defaults) {
        Map<String, String> m = new HashMap<>(defaults);
        for (int i = 0; i < header.length && i < row.length; i++) {
            m.put(header[i], row[i]);
        }
        String accountId = coalesce(m, "account_id", "card_no");
        Instant occurredAt = parseDate(coalesce(m, "occurred_at", "transaction_date"));
        Instant postedAt = parseDate(coalesce(m, "posted_at", "posted_date", "post_date"));
        long amountCents = parseAmount(m);
        String currency = m.getOrDefault("currency", defaults.getOrDefault("currency", "USD"));
        String merchant = coalesce(m, "merchant", "description");
        String category = m.get("category");
        String type = m.get("type");
        String memo = m.get("memo");
        String source = m.getOrDefault("source", defaults.get("source"));
        String rawJson = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(m).toString();
        String occurred = occurredAt == null ? "" : occurredAt.toString();
        String hash = DigestUtils.sha256Hex(accountId + amountCents + occurred + merchant);
        if ("capitalone".equals(source)) {
            return new CapitalOneVentureXTransaction(accountId, occurredAt, postedAt, amountCents,
                    currency, merchant, category, type, memo, hash, rawJson);
        }
        return new GenericTransaction(accountId, occurredAt, postedAt, amountCents, currency, merchant,
                category, type, memo, hash, rawJson, source);
    }

    private long parseAmount(Map<String, String> m) {
        if (m.containsKey("amount_cents")) {
            return Long.parseLong(m.get("amount_cents"));
        }
        if (m.containsKey("amount")) {
            return toCents(new BigDecimal(m.get("amount")));
        }
        String credit = m.get("credit");
        if (credit != null && !credit.isBlank()) {
            return toCents(new BigDecimal(credit));
        }
        String debit = m.get("debit");
        if (debit != null && !debit.isBlank()) {
            return -toCents(new BigDecimal(debit));
        }
        return 0;
    }

    private long toCents(BigDecimal v) {
        return v.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private Instant parseDate(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return Instant.parse(v);
        } catch (DateTimeParseException e) {
            try {
                LocalDate d = LocalDate.parse(v, DateTimeFormatter.ISO_DATE);
                return d.atStartOfDay(ZoneOffset.UTC).toInstant();
            } catch (DateTimeParseException e2) {
                LocalDate d = LocalDate.parse(v, DateTimeFormatter.ofPattern("MM/dd/yyyy"));
                return d.atStartOfDay(ZoneOffset.UTC).toInstant();
            }
        }
    }

    private String normalize(String h) {
        return h.toLowerCase()
                .replaceAll("[. ]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    private String coalesce(Map<String, String> m, String... keys) {
        for (String k : keys) {
            String v = m.get(k);
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
