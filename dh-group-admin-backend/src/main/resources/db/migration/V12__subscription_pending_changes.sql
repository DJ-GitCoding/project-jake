-- Let a template change be proposed to an individual subscriber rather than forced on them.
DO $$
BEGIN
    IF to_regclass('public.agreement_subscriptions') IS NOT NULL THEN
        ALTER TABLE agreement_subscriptions
            ADD COLUMN IF NOT EXISTS pending_template_snapshot JSONB,
            ADD COLUMN IF NOT EXISTS pending_change_status VARCHAR(20),
            ADD COLUMN IF NOT EXISTS pending_proposed_at TIMESTAMP,
            ADD COLUMN IF NOT EXISTS pending_responded_at TIMESTAMP;
    END IF;
END $$;
