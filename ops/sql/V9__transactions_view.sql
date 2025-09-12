CREATE OR REPLACE VIEW transactions_view AS
SELECT
    id,
    occurred_at,
    posted_at,
    amount_cents,
    currency,
    merchant,
    category,
    txn_type,
    memo,
    hash,
    raw_json,
    created_at,
    account_id,
    'chase'::text AS institution
FROM
    chase_transactions
UNION ALL
SELECT
    id,
    occurred_at,
    posted_at,
    amount_cents,
    currency,
    merchant,
    category,
    txn_type,
    memo,
    hash,
    raw_json,
    created_at,
    account_id,
    'capital_one'::text AS institution
FROM
    capital_one_transactions;
