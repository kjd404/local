ALTER TABLE chase_transactions
    ADD COLUMN account_id BIGINT NOT NULL REFERENCES accounts(id);

ALTER TABLE capital_one_transactions
    ADD COLUMN account_id BIGINT NOT NULL REFERENCES accounts(id);

DROP INDEX IF EXISTS chase_transactions_hash_idx;
CREATE UNIQUE INDEX chase_transactions_account_hash_idx
    ON chase_transactions(account_id, hash);

DROP INDEX IF EXISTS capital_one_transactions_hash_idx;
CREATE UNIQUE INDEX capital_one_transactions_account_hash_idx
    ON capital_one_transactions(account_id, hash);
