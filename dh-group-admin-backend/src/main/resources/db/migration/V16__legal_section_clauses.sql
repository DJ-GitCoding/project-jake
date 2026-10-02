DO $$
BEGIN
    IF to_regclass('public.agreement_legal_sections') IS NOT NULL THEN
        ALTER TABLE agreement_legal_sections
            ADD COLUMN IF NOT EXISTS ref_key VARCHAR(100),
            ADD COLUMN IF NOT EXISTS clauses JSONB;

        UPDATE agreement_legal_sections
        SET ref_key = 'section-' || id::text
        WHERE ref_key IS NULL;
    END IF;
END $$;
