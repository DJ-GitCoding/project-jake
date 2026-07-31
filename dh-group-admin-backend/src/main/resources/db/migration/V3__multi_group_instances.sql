-- V3: Allow data holder instances to be associated with multiple data holder groups
--
-- NOTE: Flyway runs BEFORE Hibernate ddl-auto on a fresh database. data_holder_instances,
-- data_holder_groups and the join table (data_holder_instance_group_memberships is a
-- @JoinTable on DataHolderInstance) are all Hibernate-managed. On a brand-new environment
-- data_holder_groups does not exist yet, so the join table's foreign key cannot be created
-- here; Hibernate will create the whole structure instead. We therefore only run this
-- migration when data_holder_groups already exists (an existing database).
DO $$
BEGIN
    IF to_regclass('public.data_holder_groups') IS NOT NULL THEN

        -- Ensure the data_holder_instances table exists (normally created by Hibernate
        -- ddl-auto, but kept here for older databases that predate the instance table).
        CREATE TABLE IF NOT EXISTS data_holder_instances (
            id                    BIGSERIAL PRIMARY KEY,
            subdomain             VARCHAR(30) NOT NULL UNIQUE,
            name                  VARCHAR(255) NOT NULL,
            dataholder_id         VARCHAR(255),
            data_holder_group_id  BIGINT,
            backend_port          INTEGER,
            frontend_port         INTEGER,
            backend_container     VARCHAR(255),
            frontend_container    VARCHAR(255),
            database_name         VARCHAR(255),
            admin_username        VARCHAR(255) DEFAULT 'admin',
            admin_password        VARCHAR(255),
            status                VARCHAR(255) NOT NULL DEFAULT 'running',
            url                   VARCHAR(255),
            backend_image         VARCHAR(255),
            frontend_image        VARCHAR(255),
            created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
            updated_at            TIMESTAMP
        );

        CREATE INDEX IF NOT EXISTS idx_dhi_subdomain ON data_holder_instances(subdomain);

        CREATE TABLE IF NOT EXISTS data_holder_instance_group_memberships (
            instance_id          BIGINT NOT NULL REFERENCES data_holder_instances(id) ON DELETE CASCADE,
            data_holder_group_id BIGINT NOT NULL REFERENCES data_holder_groups(id) ON DELETE CASCADE,
            PRIMARY KEY (instance_id, data_holder_group_id)
        );

        CREATE INDEX IF NOT EXISTS idx_dhigm_instance ON data_holder_instance_group_memberships(instance_id);
        CREATE INDEX IF NOT EXISTS idx_dhigm_group ON data_holder_instance_group_memberships(data_holder_group_id);

        -- Migrate existing single-group associations into the join table
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'data_holder_instances' AND column_name = 'data_holder_group_id'
        ) THEN
            INSERT INTO data_holder_instance_group_memberships (instance_id, data_holder_group_id)
            SELECT id, data_holder_group_id
            FROM data_holder_instances
            WHERE data_holder_group_id IS NOT NULL
            ON CONFLICT DO NOTHING;
        END IF;
    END IF;
END $$;
