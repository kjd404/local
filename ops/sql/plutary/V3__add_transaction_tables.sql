CREATE TABLE receipt_transactions (
    receipt_ingestion_id uuid PRIMARY KEY REFERENCES receipt_ingestions (id) ON DELETE CASCADE,
    merchant text NOT NULL,
    total_amount_cents bigint NOT NULL,
    currency text NOT NULL,
    occurred_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE receipt_line_items (
    id uuid PRIMARY KEY,
    receipt_transaction_id uuid NOT NULL REFERENCES receipt_transactions (receipt_ingestion_id) ON DELETE CASCADE,
    line_number integer NOT NULL,
    description text NOT NULL,
    quantity integer,
    amount_cents bigint,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX receipt_line_items_receipt_idx ON receipt_line_items (receipt_transaction_id);
