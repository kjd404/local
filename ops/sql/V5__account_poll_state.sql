ALTER TABLE accounts ADD COLUMN backfilled_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS account_poll_state (
    account_id BIGINT PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    cursor TEXT,
    updated_at TIMESTAMPTZ DEFAULT now()
);
