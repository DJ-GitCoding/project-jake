-- V3__branding_asset.sql
-- Single-row store for the deployment's custom logo.
--
-- An absent row means "use the built-in logo"; uploading writes id=1 and removing
-- deletes it. Bytes live in the database so no persistent volume is required and the
-- logo survives container replacement.
--
-- Hibernate runs with ddl-auto=none here, so this migration is the only thing that
-- creates the table. IF NOT EXISTS keeps it safe to re-run against a database that was
-- baselined or repaired out of order (see V2__baseline_repair.sql).

CREATE TABLE IF NOT EXISTS branding_asset (
    id           BIGINT PRIMARY KEY,
    filename     VARCHAR(255),
    content_type VARCHAR(100) NOT NULL,
    data         BYTEA        NOT NULL,
    size_bytes   INTEGER      NOT NULL,
    updated_at   TIMESTAMP,
    updated_by   VARCHAR(100)
);
