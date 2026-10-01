CREATE TABLE budget_idempotency (
    request_key VARCHAR(100) PRIMARY KEY,
    payload_hash CHAR(64) NOT NULL,
    budget_request_id UUID NOT NULL REFERENCES budget_requests(id),
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT idempotency_key_format CHECK (request_key ~ '^[A-Za-z0-9_-]{16,100}$')
);
CREATE INDEX idx_idempotency_expiration ON budget_idempotency(expires_at);
