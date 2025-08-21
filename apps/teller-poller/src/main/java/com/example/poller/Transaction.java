package com.example.poller;

import java.time.Instant;

public class Transaction {
    public String accountId;
    public Long accountPk;
    public Instant occurredAt;
    public Instant postedAt;
    public long amountCents;
    public String currency;
    public String merchant;
    public String category;
    public String type;
    public String memo;
    public String source;
    public String hash;
    public String rawJson;
}
