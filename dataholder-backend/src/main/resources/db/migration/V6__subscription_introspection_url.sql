-- V6__subscription_introspection_url.sql
-- Add introspection URL to agreement subscriptions.
-- This allows requestor groups to provide their own token introspection
-- endpoint when subscribing, so data holders can validate bearer tokens
-- without requiring a dedicated Keycloak client credential.

ALTER TABLE agreement_subscriptions
    ADD COLUMN IF NOT EXISTS introspection_url VARCHAR(500);

COMMENT ON COLUMN agreement_subscriptions.introspection_url IS
    'Token introspection URL provided by requestor group at subscription time';
