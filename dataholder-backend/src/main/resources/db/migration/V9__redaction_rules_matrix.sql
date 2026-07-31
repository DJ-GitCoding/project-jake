-- V9__redaction_rules_matrix.sql
-- Replace element-based redaction rules with a 32-row matrix model.
--
-- The new model is triggered by three conditions:
--   1. Request Type's Access Level (0-3)
--   2. Policy's Sensitivity Level (0-3)
--   3. Whether the data element's value is empty or not
--
-- Behavior is one of: FULL (return value), EMPTY (return nothing), REDACTED (return "REDACTED")
-- 4 x 4 x 2 = 32 rows.

-- Step 1: Drop the old element-based redaction_rules table
DROP TABLE IF EXISTS redaction_rules CASCADE;

-- Step 2: Create the new matrix-based redaction_rules table
CREATE TABLE redaction_rules (
    id BIGSERIAL PRIMARY KEY,
    access_level INTEGER NOT NULL CHECK (access_level BETWEEN 0 AND 3),
    sensitivity_level INTEGER NOT NULL CHECK (sensitivity_level BETWEEN 0 AND 3),
    value_is_empty BOOLEAN NOT NULL,
    redaction_behavior VARCHAR(20) NOT NULL DEFAULT 'FULL'
        CHECK (redaction_behavior IN ('FULL', 'EMPTY', 'REDACTED')),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_redaction_rule_combo UNIQUE (access_level, sensitivity_level, value_is_empty)
);

CREATE INDEX idx_redaction_rules_active ON redaction_rules(is_active);

COMMENT ON TABLE redaction_rules IS '32-row matrix defining redaction behavior for each combination of access level, sensitivity level, and value emptiness';
COMMENT ON COLUMN redaction_rules.access_level IS 'The request type access level (0-3)';
COMMENT ON COLUMN redaction_rules.sensitivity_level IS 'The policy sensitivity level (0-3)';
COMMENT ON COLUMN redaction_rules.value_is_empty IS 'Whether the data element value is empty';
COMMENT ON COLUMN redaction_rules.redaction_behavior IS 'FULL = return value, EMPTY = return nothing, REDACTED = return "REDACTED"';

-- Step 3: Seed the 32 default rows.
-- Default logic:
--   If value is empty → EMPTY
--   If access_level >= sensitivity_level → FULL
--   Otherwise → REDACTED
INSERT INTO redaction_rules (access_level, sensitivity_level, value_is_empty, redaction_behavior) VALUES
-- access_level 0
(0, 0, FALSE, 'FULL'),
(0, 0, TRUE,  'EMPTY'),
(0, 1, FALSE, 'REDACTED'),
(0, 1, TRUE,  'EMPTY'),
(0, 2, FALSE, 'REDACTED'),
(0, 2, TRUE,  'EMPTY'),
(0, 3, FALSE, 'REDACTED'),
(0, 3, TRUE,  'EMPTY'),
-- access_level 1
(1, 0, FALSE, 'FULL'),
(1, 0, TRUE,  'EMPTY'),
(1, 1, FALSE, 'FULL'),
(1, 1, TRUE,  'EMPTY'),
(1, 2, FALSE, 'REDACTED'),
(1, 2, TRUE,  'EMPTY'),
(1, 3, FALSE, 'REDACTED'),
(1, 3, TRUE,  'EMPTY'),
-- access_level 2
(2, 0, FALSE, 'FULL'),
(2, 0, TRUE,  'EMPTY'),
(2, 1, FALSE, 'FULL'),
(2, 1, TRUE,  'EMPTY'),
(2, 2, FALSE, 'FULL'),
(2, 2, TRUE,  'EMPTY'),
(2, 3, FALSE, 'REDACTED'),
(2, 3, TRUE,  'EMPTY'),
-- access_level 3
(3, 0, FALSE, 'FULL'),
(3, 0, TRUE,  'EMPTY'),
(3, 1, FALSE, 'FULL'),
(3, 1, TRUE,  'EMPTY'),
(3, 2, FALSE, 'FULL'),
(3, 2, TRUE,  'EMPTY'),
(3, 3, FALSE, 'FULL'),
(3, 3, TRUE,  'EMPTY');

-- Step 4: Fix policy_redaction_rules min_access_level constraint (cap at 3 not 4)
UPDATE policy_redaction_rules SET min_access_level = 3 WHERE min_access_level > 3;
COMMENT ON COLUMN policy_redaction_rules.min_access_level IS 'Minimum access level (0-3) required to see field unredacted';

-- Step 5: Add sensitivity_level to policy_expressions
ALTER TABLE policy_expressions
    ADD COLUMN IF NOT EXISTS sensitivity_level INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN policy_expressions.sensitivity_level IS 'Sensitivity level (0-3) of data governed by this policy. Used with access level to look up redaction behavior.';
