/*
 * Access levels are carried by the request type. The subscription-wide granted level and
 * the agreement-wide level both predate that: neither could describe an agreement whose
 * request types grant different levels, and Jaddar reads only the per-request-type value.
 *
 * Guarded on each table existing — Flyway runs before Hibernate creates the schema on a
 * fresh database, where there is nothing to drop.
 */
DO $$
BEGIN
    IF to_regclass('public.subscription_requests') IS NOT NULL THEN
        ALTER TABLE subscription_requests DROP COLUMN IF EXISTS granted_access_level;
    END IF;

    IF to_regclass('public.data_holder_agreements') IS NOT NULL THEN
        ALTER TABLE data_holder_agreements DROP COLUMN IF EXISTS access_level;
    END IF;
END $$;
