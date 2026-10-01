CREATE TABLE admin_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(10) NOT NULL CHECK (role IN ('ADMIN', 'DEMO')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT admin_email_normalized CHECK (email = lower(btrim(email)) AND email <> '')
);

CREATE TABLE service_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order SMALLINT NOT NULL UNIQUE,
    CONSTRAINT service_code_not_blank CHECK (btrim(code) <> ''),
    CONSTRAINT service_name_not_blank CHECK (btrim(name) <> '')
);

CREATE TABLE budget_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    protocol VARCHAR(40) NOT NULL UNIQUE CHECK (btrim(protocol) <> ''),
    customer_name VARCHAR(100) NOT NULL CHECK (btrim(customer_name) <> ''),
    phone VARCHAR(13) NOT NULL CHECK (phone ~ '^(55)?[1-9][0-9][2-9][0-9]{7,8}$'),
    service_type_id UUID NOT NULL REFERENCES service_types(id),
    message VARCHAR(2000),
    status VARCHAR(20) NOT NULL DEFAULT 'NEW'
        CHECK (status IN ('NEW', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_budget_status_created ON budget_requests(status, created_at DESC);
CREATE INDEX idx_budget_service_created ON budget_requests(service_type_id, created_at DESC);
CREATE INDEX idx_budget_created ON budget_requests(created_at DESC, id);

CREATE TABLE budget_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    budget_request_id UUID NOT NULL REFERENCES budget_requests(id),
    previous_status VARCHAR(20)
        CHECK (previous_status IN ('NEW', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    new_status VARCHAR(20) NOT NULL
        CHECK (new_status IN ('NEW', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    changed_by UUID REFERENCES admin_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT history_status_changed CHECK (previous_status IS NULL OR previous_status <> new_status)
);
CREATE INDEX idx_history_budget_created ON budget_status_history(budget_request_id, created_at);
CREATE INDEX idx_history_author ON budget_status_history(changed_by);

CREATE TABLE budget_notes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    budget_request_id UUID NOT NULL REFERENCES budget_requests(id),
    author_id UUID NOT NULL REFERENCES admin_users(id),
    text VARCHAR(2000) NOT NULL CHECK (btrim(text) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_notes_budget_created ON budget_notes(budget_request_id, created_at);
CREATE INDEX idx_notes_author ON budget_notes(author_id);
