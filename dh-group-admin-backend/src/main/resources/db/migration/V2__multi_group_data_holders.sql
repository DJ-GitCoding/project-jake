-- V2: Allow data holders to belong to multiple data holder groups
-- Creates a join table and migrates existing single-group relationships.
--
-- NOTE: Flyway runs BEFORE Hibernate ddl-auto on a fresh database. The referenced
-- tables (data_holders, data_holder_groups) and the join table itself are all
-- Hibernate-managed (data_holder_group_memberships is a @JoinTable on DataHolder),
-- so on a brand-new environment they do not exist yet and Hibernate will create the
-- join table on its own. We therefore only run this migration when the parent tables
-- already exist (an existing database that needs the back-fill).
DO $$
BEGIN
    IF to_regclass('public.data_holders') IS NOT NULL
       AND to_regclass('public.data_holder_groups') IS NOT NULL THEN

        -- Step 1: Create the many-to-many join table
        CREATE TABLE IF NOT EXISTS data_holder_group_memberships (
            data_holder_id  BIGINT NOT NULL REFERENCES data_holders(id) ON DELETE CASCADE,
            data_holder_group_id BIGINT NOT NULL REFERENCES data_holder_groups(id) ON DELETE CASCADE,
            PRIMARY KEY (data_holder_id, data_holder_group_id)
        );

        CREATE INDEX IF NOT EXISTS idx_dhgm_data_holder ON data_holder_group_memberships(data_holder_id);
        CREATE INDEX IF NOT EXISTS idx_dhgm_group ON data_holder_group_memberships(data_holder_group_id);

        -- Step 2: Migrate existing single-group associations into the join table.
        -- Only when the legacy single-group column is present.
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'data_holders' AND column_name = 'data_holder_group_id'
        ) THEN
            INSERT INTO data_holder_group_memberships (data_holder_id, data_holder_group_id)
            SELECT id, data_holder_group_id
            FROM data_holders
            WHERE data_holder_group_id IS NOT NULL
            ON CONFLICT DO NOTHING;
        END IF;
    END IF;
END $$;

-- Note: We keep the data_holders.data_holder_group_id column for backward compatibility.
-- It will be maintained in sync by the application but the join table is authoritative.
