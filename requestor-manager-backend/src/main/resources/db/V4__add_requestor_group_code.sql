-- V4__Add_Requestor_Group_Code.sql
-- Adds 'code' column to requestor_groups for RDAP query identification.
-- Format: ≤10 chars, letters/digits/hyphens, no leading/trailing hyphen.

ALTER TABLE requestor_groups ADD COLUMN IF NOT EXISTS code VARCHAR(10) UNIQUE;

COMMENT ON COLUMN requestor_groups.code IS 
    'Short identifier for RDAP query parameters. ≤10 chars, alphanumeric + hyphens.';