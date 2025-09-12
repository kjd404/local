-- Canonical transactions schema
CREATE TABLE IF NOT EXISTS transactions (
    id bigserial PRIMARY KEY,
    account_id text NOT NULL,
    occurred_at timestamptz,
    posted_at timestamptz,
    amount_cents bigint NOT NULL,
    currency text NOT NULL DEFAULT 'USD',
    merchant text,
    category text,
    memo text,
    source text NOT NULL,
    hash TEXT NOT NULL,
    raw_json jsonb NOT NULL,
    created_at timestamptz DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS transactions_account_hash_idx ON transactions (account_id, hash);
