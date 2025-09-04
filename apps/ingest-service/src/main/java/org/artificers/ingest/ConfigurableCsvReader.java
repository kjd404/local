package org.artificers.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

public class ConfigurableCsvReader extends BaseCsvReader implements TransactionCsvReader {
    private final String institution;
    private final Map<String, FieldSpec> fields;

    public ConfigurableCsvReader(Mapping mapping) {
        this.institution = mapping.institution();
        this.fields = mapping.fields();
    }

    @Override
    public String institution() {
        return institution;
    }

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
        Map<String, String> raw = new LinkedHashMap<>();
        Instant occurredAt = null;
        Instant postedAt = null;
        long amountCents = 0;
        String currency = "USD";
        String merchant = null;
        String category = null;
        String type = null;
        String memo = null;

        for (int i = 0; i < header.length && i < row.length; i++) {
            String h = header[i];
            String v = row[i];
            FieldSpec spec = fields.get(h);
            if (spec != null) {
                String target = spec.target();
                switch (target) {
                    case "occurred_at" -> occurredAt = parseTimestamp(v, spec.format());
                    case "posted_at" -> postedAt = parseTimestamp(v, spec.format());
                    case "amount_cents" -> {
                        if ("currency".equals(spec.type())) {
                            long cents = parseAmount(v);
                            if (h.contains("debit")) {
                                amountCents -= cents;
                            } else {
                                amountCents += cents;
                            }
                        } else if ("int".equals(spec.type())) {
                            long cents = Long.parseLong(v);
                            if (h.contains("debit")) {
                                amountCents -= cents;
                            } else {
                                amountCents += cents;
                            }
                        }
                    }
                    case "currency" -> currency = v;
                    case "merchant" -> merchant = v;
                    case "category" -> category = v;
                    case "type" -> type = v;
                    case "memo" -> memo = v;
                    default -> {
                        raw.put(h, v);
                    }
                }
            } else {
                raw.put(h, v);
            }
        }

        String rawJson = new ObjectMapper().valueToTree(raw).toString();
        String occurred = occurredAt == null ? "" : occurredAt.toString();
        String hash = DigestUtils.sha256Hex(accountId + amountCents + occurred + merchant);
        TransactionRecord tx = new GenericTransaction(accountId, occurredAt, postedAt, amountCents,
                currency, merchant, category, type, memo, hash, rawJson);
        TransactionValidator.validate(tx);
        return tx;
    }

    private Instant parseTimestamp(String v, String format) {
        if (v == null || v.isBlank()) return null;
        if (format != null && !format.isBlank()) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern(format).withZone(ZoneOffset.UTC);
            try {
                return Instant.from(fmt.parse(v));
            } catch (DateTimeParseException e) {
                LocalDate d = LocalDate.parse(v, fmt);
                return d.atStartOfDay(ZoneOffset.UTC).toInstant();
            }
        }
        return parseDate(v);
    }

    public record Mapping(String institution, Map<String, FieldSpec> fields) {}

    public record FieldSpec(String target, String type, String format) {}
}
