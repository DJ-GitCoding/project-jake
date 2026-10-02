-- Add custom_table_mappings JSONB column to rdap_data_mappings
-- Stores custom table definitions with column-level mappings to standard fields or custom aliases
ALTER TABLE rdap_data_mappings
    ADD COLUMN IF NOT EXISTS custom_table_mappings jsonb;

-- Whether column name lookups should be case-sensitive (default false for MariaDB/MySQL compatibility)
ALTER TABLE rdap_data_mappings
    ADD COLUMN IF NOT EXISTS column_name_case_sensitive boolean DEFAULT false;

COMMENT ON COLUMN rdap_data_mappings.custom_table_mappings IS
    'JSON array of custom table mapping objects. Each entry defines a table name, label, and column mappings that map source columns to either standard RDAP fields or user-defined custom aliases.';

COMMENT ON COLUMN rdap_data_mappings.column_name_case_sensitive IS
    'When false (default), column name lookups for join conditions are case-insensitive. Set to true for databases like PostgreSQL where column names are case-sensitive.';
