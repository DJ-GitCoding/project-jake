-- V5__data_holder_registry_settings.sql
-- Single-row store for the external Data Holder Registry settings (id=1).

CREATE TABLE IF NOT EXISTS data_holder_registry_settings (
    id                BIGINT PRIMARY KEY,
    enabled           BOOLEAN       NOT NULL DEFAULT FALSE,
    registry_url      VARCHAR(1024),
    last_synced_at    TIMESTAMP,
    last_sync_status  VARCHAR(32),
    last_sync_message VARCHAR(1024),
    holder_count      INTEGER,
    feed_publication  VARCHAR(64),
    updated_by        VARCHAR(255),
    updated_at        TIMESTAMP
);
