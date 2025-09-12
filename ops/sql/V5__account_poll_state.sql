ALTER TABLE accounts
    ADD COLUMN backfilled_at timestamptz;

CREATE TABLE IF NOT EXISTS account_poll_state (
    account_id bigint PRIMARY KEY REFERENCES accounts (id) ON DELETE CASCADE,
    cursor TEXT,
    updated_at timestamptz DEFAULT now()
);
