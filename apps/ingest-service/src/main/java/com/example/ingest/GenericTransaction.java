package com.example.ingest;

import java.time.Instant;

public final class GenericTransaction implements TransactionRecord {
    private final String accountId;
    private final Instant occurredAt;
    private final Instant postedAt;
    private final long amountCents;
    private final String currency;
    private final String merchant;
    private final String category;
    private final String type;
    private final String memo;
    private final String hash;
    private final String rawJson;

    public GenericTransaction(
            String accountId,
            Instant occurredAt,
            Instant postedAt,
            long amountCents,
            String currency,
            String merchant,
            String category,
            String type,
            String memo,
            String hash,
            String rawJson) {
        this.accountId = accountId;
        this.occurredAt = occurredAt;
        this.postedAt = postedAt;
        this.amountCents = amountCents;
        this.currency = currency;
        this.merchant = merchant;
        this.category = category;
        this.type = type;
        this.memo = memo;
        this.hash = hash;
        this.rawJson = rawJson;
    }

    @Override
    public String accountId() { return accountId; }

    @Override
    public Instant occurredAt() { return occurredAt; }

    @Override
    public Instant postedAt() { return postedAt; }

    @Override
    public long amountCents() { return amountCents; }

    @Override
    public String currency() { return currency; }

    @Override
    public String merchant() { return merchant; }

    @Override
    public String category() { return category; }

    @Override
    public String type() { return type; }

    @Override
    public String memo() { return memo; }

    @Override
    public String hash() { return hash; }

    @Override
    public String rawJson() { return rawJson; }

    @Override
    public String toString() {
        return "GenericTransaction{" +
                "accountId='" + accountId + '\'' +
                ", occurredAt=" + occurredAt +
                ", postedAt=" + postedAt +
                ", amountCents=" + amountCents +
                ", currency='" + currency + '\'' +
                ", merchant='" + merchant + '\'' +
                ", category='" + category + '\'' +
                ", type='" + type + '\'' +
                ", memo='" + memo + '\'' +
                ", hash='" + hash + '\'' +
                '}';
    }
}
