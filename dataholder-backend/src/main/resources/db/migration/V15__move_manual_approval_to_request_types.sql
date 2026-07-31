-- Move requires_manual_approval from agreement_templates to agreement_request_types
-- This allows per-request-type control over whether subscriptions need manual approval.

-- Step 1: Add the new column to agreement_request_types
ALTER TABLE agreement_request_types
    ADD COLUMN IF NOT EXISTS requires_manual_approval BOOLEAN NOT NULL DEFAULT true;

-- Step 2: Migrate existing values from the template level to each request type
UPDATE agreement_request_types rt
    SET requires_manual_approval = COALESCE(t.requires_manual_approval, true)
    FROM agreement_templates t
    WHERE rt.template_id = t.id;

-- Step 3: Drop the old column from agreement_templates
ALTER TABLE agreement_templates
    DROP COLUMN IF EXISTS requires_manual_approval;
