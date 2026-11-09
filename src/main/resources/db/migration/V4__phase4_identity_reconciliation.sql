-- Phase 4 identity and reconciliation schema. V1-V3 are immutable.
CREATE TABLE users (
  id UUID PRIMARY KEY,
  customer_id UUID REFERENCES customers(id),
  email VARCHAR(320) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_users_email UNIQUE (email),
  CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE','SUSPENDED','DISABLED'))
);
CREATE INDEX ix_users_customer_id ON users(customer_id);
CREATE TABLE roles (
  id UUID PRIMARY KEY,
  name VARCHAR(30) NOT NULL UNIQUE,
  CONSTRAINT ck_roles_name CHECK (name IN ('CUSTOMER','OPERATIONS','ADMIN'))
);
CREATE TABLE user_roles (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role_id UUID NOT NULL REFERENCES roles(id),
  PRIMARY KEY (user_id, role_id)
);
CREATE TABLE refresh_tokens (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  family_id UUID NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  replaced_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens(family_id);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens(user_id);
INSERT INTO roles(id,name) VALUES
  ('10000000-0000-0000-0000-000000000001','CUSTOMER'),
  ('10000000-0000-0000-0000-000000000002','OPERATIONS'),
  ('10000000-0000-0000-0000-000000000003','ADMIN');

CREATE TABLE reconciliation_runs (
  id UUID PRIMARY KEY,
  status VARCHAR(20) NOT NULL,
  started_at TIMESTAMPTZ NOT NULL,
  completed_at TIMESTAMPTZ,
  accounts_checked BIGINT NOT NULL DEFAULT 0,
  discrepancies_found BIGINT NOT NULL DEFAULT 0,
  error_message VARCHAR(2000)
);
CREATE TABLE reconciliation_discrepancies (
  id UUID PRIMARY KEY,
  run_id UUID NOT NULL REFERENCES reconciliation_runs(id),
  discrepancy_type VARCHAR(60) NOT NULL,
  resource_type VARCHAR(60) NOT NULL,
  resource_id UUID,
  expected_value VARCHAR(300),
  actual_value VARCHAR(300),
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  detected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  resolved_at TIMESTAMPTZ,
  resolution_note VARCHAR(1000),
  CONSTRAINT ck_reconciliation_discrepancy_status CHECK (status IN ('OPEN','INVESTIGATING','RESOLVED','IGNORED'))
);
CREATE INDEX ix_reconciliation_discrepancies_run ON reconciliation_discrepancies(run_id);
CREATE INDEX ix_reconciliation_discrepancies_status ON reconciliation_discrepancies(status, detected_at DESC);
