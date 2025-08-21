package com.example.poller;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CsvTransactionMapper {
    private final AccountResolver accountResolver;

    public CsvTransactionMapper() {
        this(null);
    }

    public CsvTransactionMapper(AccountResolver accountResolver) {
        this.accountResolver = accountResolver;
    }

    public List<Transaction> parse(Reader reader) throws IOException, CsvException {
        return parse(null, reader, Map.of());
    }

    public List<Transaction> parse(Reader reader, Map<String, String> defaults) throws IOException, CsvException {
        return parse(null, reader, defaults);
    }

    public List<Transaction> parse(Path file, Reader reader) throws IOException, CsvException {
        return parse(file, reader, Map.of());
    }

    public List<Transaction> parse(Path file, Reader reader, Map<String, String> defaults) throws IOException, CsvException {
        try (CSVReader csv = new CSVReader(reader)) {
            List<String[]> rows = csv.readAll();
            String[] rawHeader = rows.remove(0);
            String[] header = Arrays.stream(rawHeader).map(this::normalize).toArray(String[]::new);
            List<Transaction> txs = rows.stream().map(r -> mapRow(header, r, defaults)).toList();
            if (accountResolver != null) {
                long accountPk = accountResolver.resolve(txs, file);
                txs.forEach(t -> t.accountPk = accountPk);
            }
            return txs;
        }
    }

    private Transaction mapRow(String[] header, String[] row, Map<String, String> defaults) {
        Map<String, String> m = new HashMap<>(defaults);
        for (int i = 0; i < header.length && i < row.length; i++) {
            m.put(header[i], row[i]);
        }
        Transaction t = new Transaction();
        String acct = coalesce(m, "account_id", "card_no");
        t.accountId = acct;
        t.occurredAt = parseDate(coalesce(m, "occurred_at", "transaction_date"));
        t.postedAt = parseDate(coalesce(m, "posted_at", "posted_date", "post_date"));
        t.amountCents = parseAmount(m);
        t.currency = m.getOrDefault("currency", defaults.getOrDefault("currency", "USD"));
        t.merchant = coalesce(m, "merchant", "description");
        t.category = m.get("category");
        t.type = m.get("type");
        t.memo = m.get("memo");
        t.source = m.getOrDefault("source", defaults.get("source"));
        t.rawJson = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(m).toString();
        String occurred = t.occurredAt == null ? "" : t.occurredAt.toString();
        t.hash = DigestUtils.sha256Hex(t.accountId + t.amountCents + occurred + t.merchant);
        return t;
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
