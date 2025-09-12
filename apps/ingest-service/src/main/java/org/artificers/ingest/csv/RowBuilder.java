package org.artificers.ingest.csv;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.artificers.ingest.model.GenericTransaction;
import org.artificers.ingest.model.TransactionRecord;
import org.artificers.ingest.validation.TransactionValidator;

class RowBuilder {
  private final TransactionValidator validator;
  private GenericTransaction.Builder builder;

  RowBuilder(String accountId, ObjectMapper mapper, TransactionValidator validator) {
    this.validator = validator;
    this.builder = new GenericTransaction.Builder(accountId, mapper);
  }

  void occurredAt(Instant v) {
    builder = builder.withOccurredAt(v);
  }

  void postedAt(Instant v) {
    builder = builder.withPostedAt(v);
  }

  void addAmount(long cents) {
    builder = builder.addAmount(cents);
  }

  void currency(String v) {
    builder = builder.withCurrency(v);
  }

  void merchant(String v) {
    builder = builder.withMerchant(v);
  }

  void category(String v) {
    builder = builder.withCategory(v);
  }

  void type(String v) {
    builder = builder.withType(v);
  }

  void memo(String v) {
    builder = builder.withMemo(v);
  }

  void raw(String h, String v) {
    builder = builder.withRaw(h, v);
  }

  TransactionRecord build() {
    TransactionRecord tx = builder.build();
    validator.validate(tx);
    return tx;
  }
}
