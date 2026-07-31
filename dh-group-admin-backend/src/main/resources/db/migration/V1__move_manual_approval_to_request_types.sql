-- Move requires_manual_approval from agreement_templates to agreement_request_types
-- This allows per-request-type control over whether subscriptions need manual approval.
--
-- NOTE: Flyway runs BEFORE Hibernate ddl-auto on a fresh database, so these tables
-- may not exist yet on a brand-new environment. This migration is purely a data
-- transformation for databases that still carry the OLD column layout; on a fresh
-- database it is a no-op and Hibernate creates the final schema (request types already
-- carry requires_manual_approval, templates no longer do).
DO $$
BEGIN
    IF to_regclass('public.agreement_request_types') IS NOT NULL
       AND EXISTS (
           SELECT 1 FROM information_schema.columns
           WHERE table_name = 'agreement_templates'
             AND column_name = 'requires_manual_approval'
       ) THEN

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
            DROP COLUMN requires_manual_approval;
    END IF;
END $$;
