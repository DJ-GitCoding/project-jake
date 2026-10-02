-- V4__branding_notices.sql
-- Deployment-defined lines shown under the sign-in form (policy wording, links, etc).
--
-- Empty by default: with no rows the login screen renders nothing extra. `text` is always
-- plain text and `url`, when set, is restricted by the service to http/https — both are
-- rendered on a page anonymous visitors can reach.
--

CREATE TABLE IF NOT EXISTS branding_notice (
    id         BIGSERIAL PRIMARY KEY,
    sort_order INTEGER      NOT NULL,
    text       VARCHAR(500) NOT NULL,
    url        VARCHAR(500),
    updated_at TIMESTAMP,
    updated_by VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_branding_notice_sort_order ON branding_notice (sort_order);
