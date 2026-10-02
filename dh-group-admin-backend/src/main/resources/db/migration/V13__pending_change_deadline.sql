-- A forced template change now carries a deadline: the requestor must accept by then or the
-- subscription is terminated. Optional changes leave the subscription untouched when declined.
DO $$
BEGIN
    IF to_regclass('public.agreement_subscriptions') IS NOT NULL THEN
        ALTER TABLE agreement_subscriptions
            ADD COLUMN IF NOT EXISTS pending_change_mode VARCHAR(20),
            ADD COLUMN IF NOT EXISTS pending_change_deadline TIMESTAMP;

        -- Proposals recorded before this migration were all optional by definition.
        UPDATE agreement_subscriptions
        SET pending_change_mode = 'OPTIONAL'
        WHERE pending_change_status = 'PROPOSED' AND pending_change_mode IS NULL;
    END IF;
END $$;
