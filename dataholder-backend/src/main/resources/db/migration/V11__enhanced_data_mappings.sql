-- V11__enhanced_data_mappings.sql
-- Enhances rdap_data_mappings to support dynamic external database targeting
-- and adds contact/host table mappings for relational schemas like a registry RDAP DB.

-- =====================================================
-- External database connection configuration
-- =====================================================
ALTER TABLE rdap_data_mappings ADD COLUMN IF NOT EXISTS db_type VARCHAR(30) DEFAULT 'LOCAL';
COMMENT ON COLUMN rdap_data_mappings.db_type IS 'LOCAL = same DB, EXTERNAL = separate JDBC connection';

ALTER TABLE rdap_data_mappings ADD COLUMN IF NOT EXISTS external_jdbc_url VARCHAR(500);
ALTER TABLE rdap_data_mappings ADD COLUMN IF NOT EXISTS external_db_username VARCHAR(100);
ALTER TABLE rdap_data_mappings ADD COLUMN IF NOT EXISTS external_db_password VARCHAR(255);
ALTER TABLE rdap_data_mappings ADD COLUMN IF NOT EXISTS external_db_driver VARCHAR(100);
ALTER TABLE rdap_data_mappings ADD COLUMN IF NOT EXISTS external_db_schema VARCHAR(100);

-- =====================================================
-- Additional table name overrides for contact and host
-- =====================================================
ALTER TABLE rdap_data_mappings ADD COLUMN IF NOT EXISTS contacts_table VARCHAR(255);
ALTER TABLE rdap_data_mappings ADD COLUMN IF NOT EXISTS hosts_table VARCHAR(255);

-- =====================================================
-- Additional column mapping overrides (JSONB)
-- =====================================================
ALTER TABLE rdap_data_mappings ADD COLUMN IF NOT EXISTS contact_column_mappings JSONB DEFAULT '{}';
ALTER TABLE rdap_data_mappings ADD COLUMN IF NOT EXISTS host_column_mappings JSONB DEFAULT '{}';

-- =====================================================
-- Join configuration: how to link domains -> contacts and domains -> hosts
-- =====================================================
-- domain_contact_join_mappings tells the system which domain columns reference which contact roles.
-- Example: {"registrant": "r_contact", "admin": "a_contact", "tech": "t_contact", "billing": "b_contact"}
-- This means: domain.r_contact = contact.id (for the registrant role)
ALTER TABLE rdap_data_mappings ADD COLUMN IF NOT EXISTS domain_contact_join_mappings JSONB DEFAULT '{}';
COMMENT ON COLUMN rdap_data_mappings.domain_contact_join_mappings IS
    'Maps contact roles to the domain table columns that hold foreign keys into the contacts table. E.g. {"registrant":"r_contact","admin":"a_contact"}';

-- contact_join_key: which column in the contacts table is the primary/join key (e.g. "id")
ALTER TABLE rdap_data_mappings ADD COLUMN IF NOT EXISTS contact_join_key VARCHAR(100) DEFAULT 'id';
COMMENT ON COLUMN rdap_data_mappings.contact_join_key IS
    'The column in the contacts table used as the join key (e.g. "id"). Domain FK columns reference this.';

-- host_join_config: how to resolve nameservers from the hosts table
-- Example: domain.nameserver is a comma-separated list of host names, host.host_name is the join key
ALTER TABLE rdap_data_mappings ADD COLUMN IF NOT EXISTS host_join_config JSONB DEFAULT '{}';
COMMENT ON COLUMN rdap_data_mappings.host_join_config IS
    'Describes how to join domain -> host. E.g. {"domainColumn":"nameserver","hostColumn":"host_name","delimiter":","}';

-- =====================================================
-- Discovered schema cache (populated by introspection)
-- =====================================================
ALTER TABLE rdap_data_mappings ADD COLUMN IF NOT EXISTS discovered_schema JSONB DEFAULT '{}';
COMMENT ON COLUMN rdap_data_mappings.discovered_schema IS
    'Cached result of database introspection: tables and their columns. Used by the UI for auto-suggest.';

ALTER TABLE rdap_data_mappings ADD COLUMN IF NOT EXISTS last_introspected_at TIMESTAMP;

-- =====================================================
-- Add indexes
-- =====================================================
CREATE INDEX IF NOT EXISTS idx_rdap_data_mappings_db_type ON rdap_data_mappings(db_type);
