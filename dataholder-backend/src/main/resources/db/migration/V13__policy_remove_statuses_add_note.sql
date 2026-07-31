-- Remove legal_status and protected_status from policy_expressions
-- Remove sensitivity_level (main sensitivity parameter)
-- Add note_to_requestor field (255 chars) returned in RDAP responses when not empty

-- Drop indexes first
DROP INDEX IF EXISTS idx_policy_expressions_legal_status;
DROP INDEX IF EXISTS idx_policy_expressions_protected_status;

-- Remove columns
ALTER TABLE policy_expressions DROP COLUMN IF EXISTS legal_status;
ALTER TABLE policy_expressions DROP COLUMN IF EXISTS protected_status;
ALTER TABLE policy_expressions DROP COLUMN IF EXISTS sensitivity_level;

-- Add note_to_requestor
ALTER TABLE policy_expressions ADD COLUMN IF NOT EXISTS note_to_requestor VARCHAR(255);

COMMENT ON COLUMN policy_expressions.note_to_requestor IS 'Optional note returned to the requestor in RDAP responses when not empty';
