-- V6__rdrs_mailbox_and_messages.sql
-- Jaddar-hosted mailboxes and captured mail for ICANN RDRS correspondence.

CREATE TABLE IF NOT EXISTS rdrs_mailboxes (
    id             BIGSERIAL PRIMARY KEY,
    user_sub       VARCHAR(255) NOT NULL UNIQUE,
    user_email     VARCHAR(255),
    local_part     VARCHAR(190) NOT NULL UNIQUE,
    first_name     VARCHAR(120),
    last_name      VARCHAR(120),
    sequence       INTEGER      NOT NULL DEFAULT 1,
    registered_at  TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL
);

CREATE TABLE IF NOT EXISTS rdrs_messages (
    id              BIGSERIAL PRIMARY KEY,
    mailbox_id      BIGINT       NOT NULL REFERENCES rdrs_mailboxes(id) ON DELETE CASCADE,
    storage_key     VARCHAR(512) NOT NULL UNIQUE,
    message_id      VARCHAR(512),
    sender          VARCHAR(512),
    recipient       VARCHAR(512),
    subject         VARCHAR(1024),
    body_plain      TEXT,
    body_html       TEXT,
    sent_at         TIMESTAMP,
    received_at     TIMESTAMP    NOT NULL,
    read_at         TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_rdrs_messages_mailbox   ON rdrs_messages (mailbox_id, received_at DESC);
CREATE INDEX IF NOT EXISTS idx_rdrs_messages_unread    ON rdrs_messages (mailbox_id) WHERE read_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_rdrs_messages_received  ON rdrs_messages (received_at);

CREATE TABLE IF NOT EXISTS rdrs_mail_settings (
    id                BIGINT PRIMARY KEY,
    retention_days    INTEGER,
    last_polled_at    TIMESTAMP,
    last_poll_status  VARCHAR(32),
    last_poll_message VARCHAR(1024),
    updated_by        VARCHAR(255),
    updated_at        TIMESTAMP
);

INSERT INTO rdrs_mail_settings (id, retention_days, updated_at)
VALUES (1, NULL, NOW())
ON CONFLICT (id) DO NOTHING;
