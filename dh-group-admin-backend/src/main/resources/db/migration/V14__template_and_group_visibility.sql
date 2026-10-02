/* Adds three level visibility to templates and groups, and renames the template published flag to active. */
DO $$
BEGIN
    IF to_regclass('public.agreement_templates') IS NOT NULL THEN
        ALTER TABLE agreement_templates
            ADD COLUMN IF NOT EXISTS is_active BOOLEAN,
            ADD COLUMN IF NOT EXISTS visibility VARCHAR(20);
        IF EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'agreement_templates' AND column_name = 'is_published') THEN
            UPDATE agreement_templates
            SET is_active = COALESCE(is_published, FALSE)
            WHERE is_active IS NULL;
        END IF;

        UPDATE agreement_templates SET is_active = FALSE WHERE is_active IS NULL;
        UPDATE agreement_templates SET visibility = 'PUBLIC' WHERE visibility IS NULL;
    END IF;

    IF to_regclass('public.data_holder_groups') IS NOT NULL THEN
        ALTER TABLE data_holder_groups
            ADD COLUMN IF NOT EXISTS visibility VARCHAR(20);
        IF EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'data_holder_groups' AND column_name = 'is_private') THEN
            UPDATE data_holder_groups
            SET visibility = CASE WHEN COALESCE(is_private, FALSE) THEN 'PRIVATE' ELSE 'PUBLIC' END
            WHERE visibility IS NULL;
        END IF;

        UPDATE data_holder_groups SET visibility = 'PUBLIC' WHERE visibility IS NULL;
    END IF;
END $$;
