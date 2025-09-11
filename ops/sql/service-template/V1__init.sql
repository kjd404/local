-- Example initial migration. Replace with your service schema.
CREATE TABLE IF NOT EXISTS example_table (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMPTZ DEFAULT now()
);

