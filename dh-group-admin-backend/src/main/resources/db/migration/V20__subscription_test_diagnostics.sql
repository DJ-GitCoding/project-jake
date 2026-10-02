-- What the last subscription test did — the queries run, the checks scored and the technical
-- cause of any failure — kept for the group admin's own screens.
DO $$
BEGIN
    IF to_regclass('public.agreement_subscriptions') IS NOT NULL THEN
        ALTER TABLE agreement_subscriptions
            ADD COLUMN IF NOT EXISTS test_diagnostics JSONB;
    END IF;
END $$;
