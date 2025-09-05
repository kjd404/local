package org.artificers.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

class RowBuilder {
    private final String accountId;
    private final ObjectMapper mapper;
    private final TransactionValidator validator;
    private final Map<String, String> raw = new LinkedHashMap<>();
    private Instant occurredAt;
    private Instant postedAt;
    private Money amount = new Money(0, "USD");
    private String merchant;
    private String category;
    private String type;
    private String memo;

    RowBuilder(String accountId, ObjectMapper mapper, TransactionValidator validator) {
        this.accountId = accountId;
        this.mapper = mapper;
        this.validator = validator;
    }

    void occurredAt(Instant v) {
        this.occurredAt = v;
    }

    void postedAt(Instant v) {
        this.postedAt = v;
    }

    void addAmount(long cents) {
        this.amount = new Money(this.amount.cents() + cents, this.amount.currency());
    }

    void currency(String v) {
        if (v != null && !v.isBlank()) {
            this.amount = new Money(this.amount.cents(), v);
        }
    }

    void merchant(String v) {
        this.merchant = v;
    }

    void category(String v) {
        this.category = v;
    }

    void type(String v) {
        this.type = v;
    }

    void memo(String v) {
        this.memo = v;
    }

    void raw(String h, String v) {
        raw.put(h, v);
    }

    TransactionRecord build() {
        String rawJson = mapper.valueToTree(raw).toString();
        String hash = HashGenerator.sha256(accountId, amount, occurredAt, merchant);
        TransactionRecord tx = new GenericTransaction(accountId, occurredAt, postedAt, amount,
                merchant, category, type, memo, hash, rawJson);
        validator.validate(tx);
        return tx;
    }
}
