-- V7: Add the request type "kind" discriminator and RDRS presets.
--
-- kind distinguishes the original RDAP request types from the new RDRS ones, which
-- submit to ICANN's Registration Data Request Service instead of a data holder.
-- rdrs_defaults holds the admin-authored presets (category, priority, permitted data
-- elements) for an RDRS request type and stays NULL for RDAP ones.
--
-- NOTE: Flyway runs BEFORE Hibernate ddl-auto on a fresh database, so
-- agreement_request_types (a Hibernate-managed table) may not exist yet. On a fresh
-- environment Hibernate creates both columns from the entity definition, so this
-- migration only needs to act on existing databases.
DO $$
BEGIN
    IF to_regclass('public.agreement_request_types') IS NOT NULL THEN
        -- Existing rows are all RDAP by definition; the DEFAULT backfills them.
        ALTER TABLE agreement_request_types
            ADD COLUMN IF NOT EXISTS kind VARCHAR(16) NOT NULL DEFAULT 'RDAP';

        ALTER TABLE agreement_request_types
            ADD COLUMN IF NOT EXISTS rdrs_defaults JSONB;
    END IF;
END $$;
