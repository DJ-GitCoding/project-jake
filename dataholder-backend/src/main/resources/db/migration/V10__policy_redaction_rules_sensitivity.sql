-- V10__policy_redaction_rules_sensitivity.sql
-- Restructure policy_redaction_rules: remove action-based columns,
-- add sensitivity_level. The redaction matrix now determines behavior;
-- these rows only map data elements to their sensitivity level per policy.

-- Step 1: Add sensitivity_level column (default 0)
ALTER TABLE policy_redaction_rules
    ADD COLUMN IF NOT EXISTS sensitivity_level INTEGER NOT NULL DEFAULT 0;

-- Step 2: Migrate existing data — derive sensitivity_level from min_access_level
UPDATE policy_redaction_rules SET sensitivity_level = LEAST(min_access_level, 3)
    WHERE min_access_level IS NOT NULL;

-- Step 3: Relax NOT NULL constraints on columns we're about to drop,
-- in case Hibernate tries to insert before the ALTER DROP completes
ALTER TABLE policy_redaction_rules ALTER COLUMN redaction_action DROP NOT NULL;
ALTER TABLE policy_redaction_rules ALTER COLUMN redaction_action SET DEFAULT NULL;
ALTER TABLE policy_redaction_rules ALTER COLUMN min_access_level DROP NOT NULL;
ALTER TABLE policy_redaction_rules ALTER COLUMN min_access_level SET DEFAULT NULL;

-- Step 4: Drop the action-related columns that are no longer used
ALTER TABLE policy_redaction_rules DROP COLUMN IF EXISTS redaction_action;
ALTER TABLE policy_redaction_rules DROP COLUMN IF EXISTS min_access_level;
ALTER TABLE policy_redaction_rules DROP COLUMN IF EXISTS replacement_value;

COMMENT ON COLUMN policy_redaction_rules.sensitivity_level IS 'Sensitivity level (0-3) for this data element. Combined with access level to look up behavior in the redaction rules matrix.';
