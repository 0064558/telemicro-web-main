CREATE TABLE budget_notifications (
    budget_id UUID PRIMARY KEY REFERENCES budget_requests(id) ON DELETE CASCADE,
    protocol VARCHAR(40) NOT NULL,
    service_name VARCHAR(100) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ
);
CREATE INDEX idx_notification_pending ON budget_notifications(next_attempt_at) WHERE sent_at IS NULL;
