-- V5__users_and_rdap_mappings.sql
-- Adds firstName, lastName, type columns to dataholder_users
-- Creates rdap_data_mappings table for custom schema mapping

-- =====================================================
-- Update dataholder_users table
-- =====================================================

-- Add first_name and last_name columns
ALTER TABLE dataholder_users ADD COLUMN IF NOT EXISTS first_name VARCHAR(100);
ALTER TABLE dataholder_users ADD COLUMN IF NOT EXISTS last_name VARCHAR(100);

-- Add type column with default ASSISTANT_ADMIN
ALTER TABLE dataholder_users ADD COLUMN IF NOT EXISTS type VARCHAR(30) NOT NULL DEFAULT 'ASSISTANT_ADMIN';

-- Ensure full_name column exists and increase length
ALTER TABLE dataholder_users ALTER COLUMN full_name TYPE VARCHAR(200);

-- Update the existing pre-seeded admin to MASTER type
UPDATE dataholder_users SET type = 'MASTER', first_name = 'System', last_name = 'Administrator'
WHERE username = 'admin' AND type = 'ASSISTANT_ADMIN';

-- =====================================================
-- RDAP Data Mappings table
-- =====================================================
CREATE TABLE IF NOT EXISTS rdap_data_mappings (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,

    -- Table name overrides
    domains_table VARCHAR(255),
    ips_table VARCHAR(255),
    asns_table VARCHAR(255),
    entities_table VARCHAR(255),

    -- Column mapping overrides (JSONB: { internalField: externalColumn })
    domain_column_mappings JSONB DEFAULT '{}',
    ip_column_mappings JSONB DEFAULT '{}',
    asn_column_mappings JSONB DEFAULT '{}',
    entity_column_mappings JSONB DEFAULT '{}',

    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rdap_data_mappings_active ON rdap_data_mappings(is_active);
CREATE UNIQUE INDEX IF NOT EXISTS idx_rdap_data_mappings_single_active ON rdap_data_mappings(is_active) WHERE is_active = TRUE;

COMMENT ON TABLE rdap_data_mappings IS 'Stores RDAP data mapping configurations to support custom database structures';
COMMENT ON COLUMN rdap_data_mappings.domains_table IS 'Override for the domains table name (default: rdap_domains)';
COMMENT ON COLUMN rdap_data_mappings.domain_column_mappings IS 'JSON mapping of internal field names to external column names for domains';
