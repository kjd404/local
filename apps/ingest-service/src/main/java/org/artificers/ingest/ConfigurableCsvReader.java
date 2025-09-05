package org.artificers.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

public class ConfigurableCsvReader extends BaseCsvReader implements TransactionCsvReader {
    private final String institution;
    private final Map<String, FieldSpec> fields;
    private final ObjectMapper mapper;

    public ConfigurableCsvReader(ObjectMapper mapper, Mapping mapping) {
        this.mapper = mapper;
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
                switch (spec.target()) {
                    case OCCURRED_AT -> occurredAt = parseTimestamp(v, spec.format());
                    case POSTED_AT -> postedAt = parseTimestamp(v, spec.format());
                    case AMOUNT_CENTS -> {
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
                    case CURRENCY -> currency = v;
                    case MERCHANT -> merchant = v;
                    case CATEGORY -> category = v;
                    case TYPE -> type = v;
                    case MEMO -> memo = v;
                    case RAW -> raw.put(h, v);
                }
            } else {
                raw.put(h, v);
            }
        }

        String rawJson = mapper.valueToTree(raw).toString();
        String hash = HashGenerator.sha256(accountId, amountCents, occurredAt, merchant);
        TransactionRecord tx = new GenericTransaction(accountId, occurredAt, postedAt, amountCents,
                currency, merchant, category, type, memo, hash, rawJson);
        TransactionValidator.validate(tx);
        return tx;
    }

    public record Mapping(String institution, Map<String, FieldSpec> fields) {}

    public record FieldSpec(FieldTarget target, String type, String format) {}
}
