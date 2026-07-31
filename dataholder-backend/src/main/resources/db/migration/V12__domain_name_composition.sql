-- V12__domain_name_composition.sql
-- Adds columns to support composing the full domain name (ldhName) from
-- a name column + a suffix/zone column (e.g. d_name + sld = "example.com")

ALTER TABLE rdap_data_mappings ADD COLUMN IF NOT EXISTS domain_name_suffix_column VARCHAR(100);
COMMENT ON COLUMN rdap_data_mappings.domain_name_suffix_column IS
    'Column in the domain table holding the TLD/zone suffix. When set, ldhName = nameCol + separator + suffixCol';

ALTER TABLE rdap_data_mappings ADD COLUMN IF NOT EXISTS domain_name_separator VARCHAR(10) DEFAULT '.';
COMMENT ON COLUMN rdap_data_mappings.domain_name_separator IS
    'Separator between domain name and suffix. Defaults to "."';
