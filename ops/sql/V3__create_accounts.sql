CREATE TABLE IF NOT EXISTS accounts (
    id bigserial PRIMARY KEY,
    institution text NOT NULL,
    external_id text NOT NULL,
    display_name text NOT NULL,
    created_at timestamptz DEFAULT now(),
    updated_at timestamptz DEFAULT now(),
    CONSTRAINT accounts_institution_external_id_key UNIQUE (institution, external_id)
);
