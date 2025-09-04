-- Consolidate institution-specific transactions into canonical table.
-- Rollback: drop new table and recreate per-institution tables from V7__split_transactions_by_account.sql, then reinsert data.
-- Compatibility: transactions_view retains prior schema and draws from unified transactions.

CREATE TABLE IF NOT EXISTS transactions (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts(id),
    occurred_at TIMESTAMPTZ,
    posted_at TIMESTAMPTZ,
    amount_cents BIGINT NOT NULL,
    currency TEXT NOT NULL DEFAULT 'USD',
    merchant TEXT,
    category TEXT,
    memo TEXT,
    txn_type TEXT,
    hash TEXT NOT NULL,
    raw_json JSONB NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS transactions_account_hash_idx
    ON transactions(account_id, hash);

INSERT INTO transactions (
    account_id,
    occurred_at,
    posted_at,
    amount_cents,
    currency,
    merchant,
    category,
    memo,
    txn_type,
    hash,
    raw_json,
    created_at
)
SELECT
    ct.account_id,
    ct.occurred_at,
    ct.posted_at,
    ct.amount_cents,
    ct.currency,
    ct.merchant,
    ct.category,
    ct.memo,
    ct.txn_type,
    ct.hash,
    ct.raw_json,
    ct.created_at
FROM chase_transactions ct
JOIN accounts a ON a.id = ct.account_id
UNION ALL
SELECT
    cot.account_id,
    cot.occurred_at,
    cot.posted_at,
    cot.amount_cents,
    cot.currency,
    cot.merchant,
    cot.category,
    cot.memo,
    cot.txn_type,
    cot.hash,
    cot.raw_json,
    cot.created_at
FROM capital_one_transactions cot
JOIN accounts a ON a.id = cot.account_id;

CREATE OR REPLACE VIEW transactions_view AS
SELECT
    t.id,
    t.occurred_at,
    t.posted_at,
    t.amount_cents,
    t.currency,
    t.merchant,
    t.category,
    t.txn_type,
    t.memo,
    t.hash,
    t.raw_json,
    t.created_at,
    t.account_id,
    a.institution
FROM transactions t
JOIN accounts a ON a.id = t.account_id;

DROP TABLE IF EXISTS chase_transactions;
DROP TABLE IF EXISTS capital_one_transactions;
