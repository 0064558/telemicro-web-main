ALTER TABLE budget_requests ADD COLUMN is_demo BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_budget_demo_created ON budget_requests(is_demo, created_at DESC, id);
