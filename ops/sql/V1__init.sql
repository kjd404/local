-- Canonical transactions schema
CREATE TABLE IF NOT EXISTS transactions (
    id BIGSERIAL PRIMARY KEY,
    account_id TEXT NOT NULL,
    occurred_at TIMESTAMPTZ,
    posted_at TIMESTAMPTZ,
    amount_cents BIGINT NOT NULL,
    currency TEXT NOT NULL DEFAULT 'USD',
    merchant TEXT,
    category TEXT,
    memo TEXT,
    source TEXT NOT NULL,
    hash TEXT NOT NULL,
    raw_json JSONB NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS transactions_account_hash_idx
    ON transactions(account_id, hash);
