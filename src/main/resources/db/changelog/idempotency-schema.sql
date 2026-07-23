-- Backs the Idempotency-Key header on POST endpoints. response_status is NULL while a
-- request is still being processed; a concurrent duplicate key hits the unique
-- constraint below and is rejected with 409 rather than racing the same write twice.
CREATE TABLE idempotency_record (
                                     id BIGSERIAL PRIMARY KEY,
                                     idempotency_key VARCHAR(255) NOT NULL,
                                     request_path VARCHAR(255) NOT NULL,
                                     response_status INTEGER,
                                     response_body TEXT,
                                     created_at TIMESTAMP NOT NULL DEFAULT now(),
                                     CONSTRAINT uq_idempotency_key_path UNIQUE (idempotency_key, request_path)
);
