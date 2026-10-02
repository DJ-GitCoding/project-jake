-- The administrator's name, alongside the contact details V17 added.
--
-- Separate from V17 because that migration has already run on deployed databases: a
-- database keeps the version it applied, so later columns need a migration of their own.
DO $$
BEGIN
    IF to_regclass('public.data_holder_groups') IS NOT NULL THEN
        ALTER TABLE data_holder_groups
            ADD COLUMN IF NOT EXISTS default_contact_first_name VARCHAR(255),
            ADD COLUMN IF NOT EXISTS default_contact_last_name VARCHAR(255);
    END IF;

    IF to_regclass('public.agreement_templates') IS NOT NULL THEN
        ALTER TABLE agreement_templates
            ADD COLUMN IF NOT EXISTS contact_first_name VARCHAR(255),
            ADD COLUMN IF NOT EXISTS contact_last_name VARCHAR(255);
    END IF;
END $$;
