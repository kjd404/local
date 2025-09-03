DROP TABLE IF EXISTS transactions;

CREATE TABLE IF NOT EXISTS chase_transactions (
    id BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMPTZ,
    posted_at TIMESTAMPTZ,
    amount_cents BIGINT NOT NULL,
    currency TEXT NOT NULL DEFAULT 'USD',
    merchant TEXT,
    category TEXT,
    txn_type TEXT,
    memo TEXT,
    hash TEXT NOT NULL,
    raw_json JSONB NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS chase_transactions_hash_idx
    ON chase_transactions(hash);

CREATE TABLE IF NOT EXISTS capital_one_transactions (
    id BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMPTZ,
    posted_at TIMESTAMPTZ,
    amount_cents BIGINT NOT NULL,
    currency TEXT NOT NULL DEFAULT 'USD',
    merchant TEXT,
    category TEXT,
    txn_type TEXT,
    memo TEXT,
    hash TEXT NOT NULL,
    raw_json JSONB NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS capital_one_transactions_hash_idx
    ON capital_one_transactions(hash);
