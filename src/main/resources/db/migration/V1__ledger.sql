CREATE TABLE department (
  id UUID PRIMARY KEY, code VARCHAR(50) NOT NULL UNIQUE,
  name VARCHAR(200) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE grant_record (
  id UUID PRIMARY KEY, award_number VARCHAR(50) NOT NULL UNIQUE,
  department_id UUID NOT NULL REFERENCES department(id), title VARCHAR(200) NOT NULL,
  sponsor_name VARCHAR(200) NOT NULL, start_date DATE NOT NULL, end_date DATE NOT NULL,
  award_amount NUMERIC(19,2) NOT NULL CHECK (award_amount > 0 AND award_amount <= 999999999.99),
  currency VARCHAR(3) NOT NULL DEFAULT 'USD' CHECK (currency = 'USD'),
  status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT','ACTIVE','CLOSED')),
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL, created_by_issuer VARCHAR(512) NOT NULL, created_by_subject VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL, updated_by_issuer VARCHAR(512) NOT NULL, updated_by_subject VARCHAR(255) NOT NULL,
  CHECK (start_date <= end_date), CHECK (award_number = upper(btrim(award_number)))
);
CREATE TABLE budget_allocation (
  id UUID PRIMARY KEY, grant_id UUID NOT NULL REFERENCES grant_record(id),
  category VARCHAR(20) NOT NULL CHECK (category IN ('PERSONNEL','EQUIPMENT','SUPPLIES','TRAVEL','OTHER')),
  allocated_amount NUMERIC(19,2) NOT NULL CHECK (allocated_amount >= 0 AND allocated_amount <= 999999999.99),
  created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
  created_by_issuer VARCHAR(512) NOT NULL, created_by_subject VARCHAR(255) NOT NULL,
  updated_by_issuer VARCHAR(512) NOT NULL, updated_by_subject VARCHAR(255) NOT NULL,
  UNIQUE (grant_id, category), UNIQUE (grant_id, id)
);
CREATE TABLE ledger_entry (
  id UUID PRIMARY KEY, grant_id UUID NOT NULL REFERENCES grant_record(id), budget_allocation_id UUID NOT NULL,
  type VARCHAR(20) NOT NULL CHECK (type IN ('EXPENSE','REVERSAL')),
  amount NUMERIC(19,2) NOT NULL CHECK (amount > 0 AND amount <= 999999999.99),
  effective_date DATE NOT NULL, external_reference VARCHAR(100), description VARCHAR(500) NOT NULL,
  original_entry_id UUID,
  created_at TIMESTAMPTZ NOT NULL, created_by_issuer VARCHAR(512) NOT NULL, created_by_subject VARCHAR(255) NOT NULL,
  UNIQUE (grant_id, id),
  FOREIGN KEY (grant_id, budget_allocation_id) REFERENCES budget_allocation(grant_id, id),
  FOREIGN KEY (grant_id, original_entry_id) REFERENCES ledger_entry(grant_id, id),
  CHECK ((type = 'EXPENSE' AND original_entry_id IS NULL AND external_reference IS NOT NULL)
    OR (type = 'REVERSAL' AND original_entry_id IS NOT NULL AND external_reference IS NULL)),
  CHECK (original_entry_id IS NULL OR original_entry_id <> id)
);
CREATE UNIQUE INDEX uq_expense_reference ON ledger_entry(grant_id, external_reference) WHERE type = 'EXPENSE';
CREATE UNIQUE INDEX uq_reversal ON ledger_entry(original_entry_id) WHERE original_entry_id IS NOT NULL;
CREATE INDEX ix_grant_department ON grant_record(department_id, status, id);
CREATE INDEX ix_entry_date ON ledger_entry(grant_id, effective_date, id);
CREATE INDEX ix_entry_budget ON ledger_entry(grant_id, budget_allocation_id);
CREATE TABLE audit_event (
  id UUID PRIMARY KEY, grant_id UUID NOT NULL REFERENCES grant_record(id), action VARCHAR(50) NOT NULL,
  resource_type VARCHAR(30) NOT NULL, resource_id UUID NOT NULL,
  actor_issuer VARCHAR(512) NOT NULL, actor_subject VARCHAR(255) NOT NULL, occurred_at TIMESTAMPTZ NOT NULL,
  before_values JSONB NOT NULL, after_values JSONB NOT NULL, reason VARCHAR(500), request_id VARCHAR(64) NOT NULL
);
CREATE INDEX ix_audit_grant ON audit_event(grant_id, occurred_at, id);
CREATE TABLE idempotency_record (
  id UUID PRIMARY KEY, actor_issuer VARCHAR(512) NOT NULL, actor_subject VARCHAR(255) NOT NULL,
  grant_id UUID NOT NULL REFERENCES grant_record(id), operation VARCHAR(100) NOT NULL, key UUID NOT NULL,
  request_hash VARCHAR(64) NOT NULL, response_status INTEGER NOT NULL CHECK (response_status = 201),
  response_body TEXT NOT NULL, response_location VARCHAR(255) NOT NULL, created_at TIMESTAMPTZ NOT NULL,
  UNIQUE (actor_issuer, actor_subject, grant_id, operation, key)
);
