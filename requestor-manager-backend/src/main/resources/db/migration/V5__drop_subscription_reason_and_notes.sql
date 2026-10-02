/*
 * The subscription form no longer collects a reason for use or additional notes: the data
 * holder group states its terms, and the requestor group answers the fields that group
 * defines. Both columns go, along with the NOT NULL on reason_for_use that would otherwise
 * reject every new subscription.
 *
 * Guarded on the table existing — Flyway runs before Hibernate creates the schema on a
 * fresh database, where there is nothing to drop.
 */
DO $$
BEGIN
    IF to_regclass('public.subscription_requests') IS NOT NULL THEN
        ALTER TABLE subscription_requests
            DROP COLUMN IF EXISTS reason_for_use,
            DROP COLUMN IF EXISTS additional_notes;
    END IF;
END $$;
