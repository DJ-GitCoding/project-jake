-- V4: Add secret_hashed flag to dataholder_credentials for BCrypt migration
--
-- NOTE: Flyway runs BEFORE Hibernate ddl-auto on a fresh database, so dataholder_credentials
-- (a Hibernate-managed table) may not exist yet. On a fresh environment Hibernate creates the
-- column from the entity definition, so this migration only needs to act on existing databases.
DO $$
BEGIN
    IF to_regclass('public.dataholder_credentials') IS NOT NULL THEN
        ALTER TABLE dataholder_credentials
            ADD COLUMN IF NOT EXISTS secret_hashed BOOLEAN NOT NULL DEFAULT false;
    END IF;
END $$;
