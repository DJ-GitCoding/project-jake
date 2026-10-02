-- V7__dataholder_config.sql
-- Global configuration table for the data holder application.
-- Single-row pattern: always exactly one row with id=1.

CREATE TABLE IF NOT EXISTS dataholder_config (
    id BIGINT PRIMARY KEY DEFAULT 1,
    require_manual_review_all BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100),
    CONSTRAINT single_row CHECK (id = 1)
);

-- Seed the singleton row
INSERT INTO dataholder_config (id, require_manual_review_all)
VALUES (1, FALSE)
ON CONFLICT (id) DO NOTHING;

COMMENT ON TABLE dataholder_config IS 'Single-row global configuration for the data holder';
COMMENT ON COLUMN dataholder_config.require_manual_review_all IS 'When true, ALL RDAP requests require manual review';
