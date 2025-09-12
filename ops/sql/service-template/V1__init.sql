-- Example initial migration. Replace with your service schema.
CREATE TABLE IF NOT EXISTS example_table (
    id bigserial PRIMARY KEY,
    created_at timestamptz DEFAULT now()
);
