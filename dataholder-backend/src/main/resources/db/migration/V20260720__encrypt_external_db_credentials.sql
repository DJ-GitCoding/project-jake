-- V20260720__encrypt_external_db_credentials.sql
-- Widens the external DB credential columns on rdap_data_mappings so they can
-- hold AES-256-GCM ciphertext (enc:v1:... base64url form) instead of plaintext.
-- Encryption itself is applied transparently by EncryptedStringConverter at the
-- JPA layer; this migration only ensures the columns are wide enough.
--
-- Guarded per the Flyway-before-Hibernate convention: on a brand-new database
-- the rdap_data_mappings table does not exist yet when Flyway runs, so this
-- becomes a no-op and Hibernate (ddl-auto=update) creates the columns at their
-- entity-defined length (512). On existing databases the ALTERs widen in place.

DO $$
BEGIN
    IF to_regclass('public.rdap_data_mappings') IS NOT NULL THEN
        ALTER TABLE rdap_data_mappings ALTER COLUMN external_db_password TYPE VARCHAR(512);
        ALTER TABLE rdap_data_mappings ALTER COLUMN external_db_username TYPE VARCHAR(512);
    END IF;
END $$;
