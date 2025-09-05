package org.artificers.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record GenericTransaction(
        String accountId,
        Instant occurredAt,
        Instant postedAt,
        Money amount,
        String merchant,
        String category,
        String type,
        String memo,
        String hash,
        String rawJson
) implements TransactionRecord {

    public static final class Builder {
        private final ObjectMapper mapper;
        private final String accountId;
        private final Instant occurredAt;
        private final Instant postedAt;
        private final Money amount;
        private final String merchant;
        private final String category;
        private final String type;
        private final String memo;
        private final Map<String, String> raw;

        public Builder(String accountId, ObjectMapper mapper) {
            this(accountId, mapper, null, null, new Money(0, "USD"), null, null, null, null, new LinkedHashMap<>());
        }

        private Builder(String accountId, ObjectMapper mapper, Instant occurredAt, Instant postedAt,
                         Money amount, String merchant, String category, String type, String memo,
                         Map<String, String> raw) {
            this.accountId = accountId;
            this.mapper = mapper;
            this.occurredAt = occurredAt;
            this.postedAt = postedAt;
            this.amount = amount;
            this.merchant = merchant;
            this.category = category;
            this.type = type;
            this.memo = memo;
            this.raw = raw;
        }

        public Builder withOccurredAt(Instant v) {
            return new Builder(accountId, mapper, v, postedAt, amount, merchant, category, type, memo, raw);
        }

        public Builder withPostedAt(Instant v) {
            return new Builder(accountId, mapper, occurredAt, v, amount, merchant, category, type, memo, raw);
        }

        public Builder addAmount(long cents) {
            Money updated = new Money(this.amount.cents() + cents, this.amount.currency());
            return new Builder(accountId, mapper, occurredAt, postedAt, updated, merchant, category, type, memo, raw);
        }

        public Builder withCurrency(String currency) {
            if (currency == null || currency.isBlank()) {
                return this;
            }
            Money updated = new Money(this.amount.cents(), currency);
            return new Builder(accountId, mapper, occurredAt, postedAt, updated, merchant, category, type, memo, raw);
        }

        public Builder withMerchant(String v) {
            return new Builder(accountId, mapper, occurredAt, postedAt, amount, v, category, type, memo, raw);
        }

        public Builder withCategory(String v) {
            return new Builder(accountId, mapper, occurredAt, postedAt, amount, merchant, v, type, memo, raw);
        }

        public Builder withType(String v) {
            return new Builder(accountId, mapper, occurredAt, postedAt, amount, merchant, category, v, memo, raw);
        }

        public Builder withMemo(String v) {
            return new Builder(accountId, mapper, occurredAt, postedAt, amount, merchant, category, type, v, raw);
        }

        public Builder withRaw(String h, String v) {
            Map<String, String> updated = new LinkedHashMap<>(raw);
            updated.put(h, v);
            return new Builder(accountId, mapper, occurredAt, postedAt, amount, merchant, category, type, memo, updated);
        }

        public GenericTransaction build() {
            String rawJson = mapper.valueToTree(raw).toString();
            String hash = HashGenerator.sha256(accountId, amount, occurredAt, merchant);
            return new GenericTransaction(accountId, occurredAt, postedAt, amount,
                    merchant, category, type, memo, hash, rawJson);
        }
    }
}
