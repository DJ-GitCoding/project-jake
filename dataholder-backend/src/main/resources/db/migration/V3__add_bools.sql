-- Migration: Add disclosure flag columns (confidential, exigent, jake_compliance)
-- to pending_requests and request_audit_log tables.

ALTER TABLE pending_requests ADD COLUMN IF NOT EXISTS confidential BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE pending_requests ADD COLUMN IF NOT EXISTS exigent BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE pending_requests ADD COLUMN IF NOT EXISTS jake_compliance BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE request_audit_log ADD COLUMN IF NOT EXISTS confidential BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE request_audit_log ADD COLUMN IF NOT EXISTS exigent BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE request_audit_log ADD COLUMN IF NOT EXISTS jake_compliance BOOLEAN NOT NULL DEFAULT FALSE;