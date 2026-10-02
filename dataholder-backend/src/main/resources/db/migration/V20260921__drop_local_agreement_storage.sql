-- Agreements belong to the data holder group, and this data holder now reads them from the
-- group admin on every use. What it kept of its own — the templates it once authored, their
-- request types and parameters, the subscriptions it once negotiated directly, and the test
-- cases pinned to those templates — is from the architecture before that, and is dropped here.
--
-- What stays: the RDAP data itself, and the records flagged as test data, which are this data
-- holder's own and are what a retrieval test is proven against.
DO $$
BEGIN
    -- The introspection credential no longer hangs off a locally held subscription.
    IF to_regclass('public.introspection_credentials') IS NOT NULL THEN
        ALTER TABLE introspection_credentials DROP COLUMN IF EXISTS subscription_id;
    END IF;

    DROP TABLE IF EXISTS agreement_template_test_data CASCADE;
    DROP TABLE IF EXISTS agreement_status_logs CASCADE;
    DROP TABLE IF EXISTS agreement_subscriptions CASCADE;
    DROP TABLE IF EXISTS agreement_requests CASCADE;
    DROP TABLE IF EXISTS agreement_request_types CASCADE;
    DROP TABLE IF EXISTS agreement_templates CASCADE;
    DROP TABLE IF EXISTS agreement_rdap_parameters CASCADE;
    DROP TABLE IF EXISTS agreement_access_levels CASCADE;

    -- Seeded in V1/V2 and unmapped since; nothing has read it in either architecture.
    DROP TABLE IF EXISTS agreements CASCADE;
END $$;
