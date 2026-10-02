/* Renames the template published flag to active. */
DO $$
BEGIN
    IF to_regclass('public.agreement_templates') IS NOT NULL THEN
        ALTER TABLE agreement_templates
            ADD COLUMN IF NOT EXISTS is_active BOOLEAN;

        IF EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'agreement_templates' AND column_name = 'is_published') THEN
            UPDATE agreement_templates
            SET is_active = COALESCE(is_published, FALSE)
            WHERE is_active IS NULL;
        END IF;

        UPDATE agreement_templates SET is_active = FALSE WHERE is_active IS NULL;
    END IF;
END $$;
