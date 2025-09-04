ALTER TABLE chase_transactions
    ADD COLUMN account_id BIGINT REFERENCES accounts(id);

ALTER TABLE capital_one_transactions
    ADD COLUMN account_id BIGINT REFERENCES accounts(id);

-- Backfill existing rows assuming one account per institution
UPDATE chase_transactions ct
SET account_id = a.id
FROM accounts a
WHERE a.institution = 'ch';

UPDATE capital_one_transactions ct
SET account_id = a.id
FROM accounts a
WHERE a.institution = 'co';

ALTER TABLE chase_transactions
    ALTER COLUMN account_id SET NOT NULL;

ALTER TABLE capital_one_transactions
    ALTER COLUMN account_id SET NOT NULL;

DROP INDEX IF EXISTS chase_transactions_hash_idx;
CREATE UNIQUE INDEX chase_transactions_account_hash_idx
    ON chase_transactions(account_id, hash);

DROP INDEX IF EXISTS capital_one_transactions_hash_idx;
CREATE UNIQUE INDEX capital_one_transactions_account_hash_idx
    ON capital_one_transactions(account_id, hash);
