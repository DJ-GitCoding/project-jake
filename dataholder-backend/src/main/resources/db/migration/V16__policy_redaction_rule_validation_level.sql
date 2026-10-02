-- Add validation_level column to policy_redaction_rules
ALTER TABLE policy_redaction_rules ADD COLUMN IF NOT EXISTS validation_level INTEGER DEFAULT NULL;

COMMENT ON COLUMN policy_redaction_rules.validation_level IS 'Validation level from imported policy (0=None/V0, 1=Syntactical/V1, 2=Operational/V2, 3=Identification/V3). NULL means not specified.';

-- Drop legacy columns that V10 was supposed to remove but may still exist
ALTER TABLE policy_redaction_rules DROP COLUMN IF EXISTS min_access_level;
ALTER TABLE policy_redaction_rules DROP COLUMN IF EXISTS redaction_action;
ALTER TABLE policy_redaction_rules DROP COLUMN IF EXISTS replacement_value;

-- Widen audit log columns that were too narrow for longer context strings
ALTER TABLE request_audit_log ALTER COLUMN query_type TYPE VARCHAR(100);
ALTER TABLE request_audit_log ALTER COLUMN result TYPE VARCHAR(50);
ALTER TABLE pending_requests ALTER COLUMN query_type TYPE VARCHAR(100);
