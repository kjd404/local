CREATE TABLE receipt_ingestions (
    id uuid PRIMARY KEY,
    client_receipt_id text,
    account_external_id text NOT NULL,
    ocr_text text NOT NULL,
    captured_at timestamp with time zone,
    status text NOT NULL DEFAULT 'RECEIVED',
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX receipt_ingestions_account_client_idx ON receipt_ingestions (account_external_id, client_receipt_id);

CREATE TABLE receipt_images (
    receipt_ingestion_id uuid PRIMARY KEY REFERENCES receipt_ingestions (id) ON DELETE CASCADE,
    content_type text,
    image_bytes bytea NOT NULL,
    byte_size integer NOT NULL,
    checksum text NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
