/*
 * Access levels belong to a request type, not to the subscription as a whole. The
 * subscription-wide column predates that and defaulted to 0, so every subscription
 * reported "Level 0" regardless of what its request types actually granted.
 *
 * granted_access_level stays: it records what the data holder group answered.
 *
 * Guarded on the table existing — Flyway runs before Hibernate creates the schema on a
 * fresh database, where there is nothing to drop.
 */
DO $$
BEGIN
    IF to_regclass('public.subscription_requests') IS NOT NULL THEN
        ALTER TABLE subscription_requests DROP COLUMN IF EXISTS requested_access_level;
    END IF;
END $$;
