-- Pin each subscription to the template as it stood when the subscription was made, so later
-- template edits do not change what an existing subscriber agreed to.
DO $$
BEGIN
    IF to_regclass('public.agreement_subscriptions') IS NOT NULL THEN
        ALTER TABLE agreement_subscriptions
            ADD COLUMN IF NOT EXISTS template_snapshot JSONB;
    END IF;
END $$;
