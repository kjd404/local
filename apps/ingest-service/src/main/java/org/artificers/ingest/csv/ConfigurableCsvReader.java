package org.artificers.ingest.csv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.util.*;
import org.artificers.ingest.model.TransactionRecord;
import org.artificers.ingest.validation.TransactionValidator;

public class ConfigurableCsvReader extends BaseCsvReader implements TransactionCsvReader {
  private final String institution;
  private final Map<String, FieldSpec> fields;
  private final ObjectMapper mapper;
  private final TransactionValidator validator;
  private final Map<FieldTarget, FieldHandler> handlers;

  public ConfigurableCsvReader(
      ObjectMapper mapper, TransactionValidator validator, Mapping mapping) {
    this.mapper = mapper;
    this.validator = validator;
    this.institution = mapping.institution();
    this.fields = mapping.fields();
    this.handlers = initHandlers();
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
    RowBuilder builder = new RowBuilder(accountId, mapper, validator);
    for (int i = 0; i < header.length && i < row.length; i++) {
      String h = header[i];
      String v = row[i];
      builder.raw(h, v);
      FieldSpec spec = fields.get(h);
      if (spec != null) {
        FieldHandler handler = handlers.get(spec.target());
        if (handler != null) {
          handler.handle(h, v, spec, builder);
        }
      }
    }
    return builder.build();
  }

  public record Mapping(String institution, Map<String, FieldSpec> fields) {}

  public record FieldSpec(FieldTarget target, String type, String format) {}

  private Map<FieldTarget, FieldHandler> initHandlers() {
    Map<FieldTarget, FieldHandler> map = new EnumMap<>(FieldTarget.class);
    map.put(
        FieldTarget.OCCURRED_AT, (h, v, spec, b) -> b.occurredAt(parseTimestamp(v, spec.format())));
    map.put(FieldTarget.POSTED_AT, (h, v, spec, b) -> b.postedAt(parseTimestamp(v, spec.format())));
    map.put(
        FieldTarget.AMOUNT_CENTS,
        (h, v, spec, b) -> {
          if ("currency".equals(spec.type())) {
            long cents = parseAmount(v);
            if (h.contains("debit")) {
              b.addAmount(-cents);
            } else {
              b.addAmount(cents);
            }
          } else if ("int".equals(spec.type())) {
            long cents = Long.parseLong(v);
            if (h.contains("debit")) {
              b.addAmount(-cents);
            } else {
              b.addAmount(cents);
            }
          }
        });
    map.put(FieldTarget.CURRENCY, (h, v, spec, b) -> b.currency(v));
    map.put(FieldTarget.MERCHANT, (h, v, spec, b) -> b.merchant(v));
    map.put(FieldTarget.CATEGORY, (h, v, spec, b) -> b.category(v));
    map.put(FieldTarget.TYPE, (h, v, spec, b) -> b.type(v));
    map.put(FieldTarget.MEMO, (h, v, spec, b) -> b.memo(v));
    map.put(FieldTarget.RAW, (h, v, spec, b) -> b.raw(h, v));
    return map;
  }
}
