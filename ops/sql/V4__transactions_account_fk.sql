ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_account_id_fkey;
DROP INDEX IF EXISTS transactions_account_hash_idx;

ALTER TABLE transactions ADD COLUMN account_id_new BIGINT;
UPDATE transactions t
SET account_id_new = a.id
FROM accounts a
WHERE a.external_id = t.account_id AND a.institution = t.source;

ALTER TABLE transactions DROP COLUMN account_id;
ALTER TABLE transactions RENAME COLUMN account_id_new TO account_id;

ALTER TABLE transactions
    ALTER COLUMN account_id SET NOT NULL,
    ADD CONSTRAINT transactions_account_id_fkey FOREIGN KEY (account_id) REFERENCES accounts(id);

CREATE UNIQUE INDEX transactions_account_hash_idx
    ON transactions(account_id, hash);
