DROP TABLE IF EXISTS transactions;

CREATE TABLE IF NOT EXISTS chase_transactions (
    id bigserial PRIMARY KEY,
    occurred_at timestamptz,
    posted_at timestamptz,
    amount_cents bigint NOT NULL,
    currency text NOT NULL DEFAULT 'USD',
    merchant text,
    category text,
    txn_type text,
    memo text,
    hash TEXT NOT NULL,
    raw_json jsonb NOT NULL,
    created_at timestamptz DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS chase_transactions_hash_idx ON chase_transactions (hash);

CREATE TABLE IF NOT EXISTS capital_one_transactions (
    id bigserial PRIMARY KEY,
    occurred_at timestamptz,
    posted_at timestamptz,
    amount_cents bigint NOT NULL,
    currency text NOT NULL DEFAULT 'USD',
    merchant text,
    category text,
    txn_type text,
    memo text,
    hash TEXT NOT NULL,
    raw_json jsonb NOT NULL,
    created_at timestamptz DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS capital_one_transactions_hash_idx ON capital_one_transactions (hash);
