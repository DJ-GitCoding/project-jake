-- V8__policy_scope_conditions.sql
-- Add scope_conditions column to policy_expressions.
-- Drop priority and default_access_level columns (no longer used by the application).

-- Step 1: Add the new column
ALTER TABLE policy_expressions
    ADD COLUMN IF NOT EXISTS scope_conditions TEXT;

-- Step 2: Drop old columns safely
-- Remove NOT NULL constraint first if it exists by setting a default, then drop
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'policy_expressions' AND column_name = 'default_access_level') THEN
        ALTER TABLE policy_expressions ALTER COLUMN default_access_level DROP NOT NULL;
        ALTER TABLE policy_expressions ALTER COLUMN default_access_level DROP DEFAULT;
        ALTER TABLE policy_expressions DROP COLUMN default_access_level;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'policy_expressions' AND column_name = 'priority') THEN
        ALTER TABLE policy_expressions DROP COLUMN priority;
    END IF;
END $$;

-- Drop the old priority index if it exists
DROP INDEX IF EXISTS idx_policy_expressions_priority;

COMMENT ON COLUMN policy_expressions.scope_conditions IS 'JSON array of scope conditions that determine when this policy applies';
