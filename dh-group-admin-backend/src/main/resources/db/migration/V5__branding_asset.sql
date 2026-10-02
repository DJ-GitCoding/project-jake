-- V5__branding_asset.sql
-- Single-row store for the deployment's custom logo.
--
-- An absent row means "use the built-in logo"; uploading writes id=1 and removing
-- deletes it. Bytes live in the database so no persistent volume is required and the
-- logo survives container replacement.
--
-- IF NOT EXISTS: Flyway runs before Hibernate, and Hibernate's ddl-auto=update may also
-- create this table on an already-migrated database. Keeping the migration idempotent
-- means a fresh database and an existing one both start cleanly.

CREATE TABLE IF NOT EXISTS branding_asset (
    id           BIGINT PRIMARY KEY,
    filename     VARCHAR(255),
    content_type VARCHAR(100) NOT NULL,
    data         BYTEA        NOT NULL,
    size_bytes   INTEGER      NOT NULL,
    updated_at   TIMESTAMP,
    updated_by   VARCHAR(100)
);
