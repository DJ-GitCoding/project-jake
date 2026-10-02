-- Contact information shown to requestors: a default on the data holder group, which
-- every template uses unless it carries its own.
--
-- NOTE: Flyway runs BEFORE Hibernate ddl-auto, so the tables may not exist yet on a fresh
-- database; the guards below leave that case to Hibernate.
DO $$
BEGIN
    IF to_regclass('public.data_holder_groups') IS NOT NULL THEN
        ALTER TABLE data_holder_groups
            ADD COLUMN IF NOT EXISTS default_contact_email VARCHAR(255),
            ADD COLUMN IF NOT EXISTS default_contact_phone VARCHAR(255),
            ADD COLUMN IF NOT EXISTS default_address VARCHAR(255),
            ADD COLUMN IF NOT EXISTS default_city VARCHAR(255),
            ADD COLUMN IF NOT EXISTS default_state_province VARCHAR(255),
            ADD COLUMN IF NOT EXISTS default_postal_code VARCHAR(255),
            ADD COLUMN IF NOT EXISTS default_country VARCHAR(255);
    END IF;

    IF to_regclass('public.agreement_templates') IS NOT NULL THEN
        ALTER TABLE agreement_templates
            ADD COLUMN IF NOT EXISTS use_group_contact BOOLEAN DEFAULT TRUE,
            ADD COLUMN IF NOT EXISTS contact_email VARCHAR(255),
            ADD COLUMN IF NOT EXISTS contact_phone VARCHAR(255),
            ADD COLUMN IF NOT EXISTS contact_address VARCHAR(255),
            ADD COLUMN IF NOT EXISTS contact_city VARCHAR(255),
            ADD COLUMN IF NOT EXISTS contact_state_province VARCHAR(255),
            ADD COLUMN IF NOT EXISTS contact_postal_code VARCHAR(255),
            ADD COLUMN IF NOT EXISTS contact_country VARCHAR(255);

        -- Templates that predate this feature show their group's contact.
        UPDATE agreement_templates SET use_group_contact = TRUE WHERE use_group_contact IS NULL;
    END IF;
END $$;
