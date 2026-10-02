-- V20260711__remove_policy_agreement_and_manual_verification.sql
-- The per-policy "Require Agreement for Enhanced Access" and "Enable Manual
-- Verification" (+ threshold) settings have been removed. Agreement enforcement
-- now falls back to the global REQUIRE_AGREEMENT config, and manual verification
-- is driven solely by the global config + request-type approval.
--
-- Drop the backing columns from both policy tables. Guarded with
-- ALTER TABLE IF EXISTS / DROP COLUMN IF EXISTS so this is a safe no-op on a
-- fresh database (Flyway runs before Hibernate). Dropping a column also drops
-- its inline CHECK constraint.

ALTER TABLE IF EXISTS policy_expressions DROP COLUMN IF EXISTS requires_manual_verification;
ALTER TABLE IF EXISTS policy_expressions DROP COLUMN IF EXISTS manual_verification_threshold;
ALTER TABLE IF EXISTS policy_expressions DROP COLUMN IF EXISTS requires_agreement;

ALTER TABLE IF EXISTS access_policies DROP COLUMN IF EXISTS requires_manual_verification;
ALTER TABLE IF EXISTS access_policies DROP COLUMN IF EXISTS manual_verification_threshold;
ALTER TABLE IF EXISTS access_policies DROP COLUMN IF EXISTS requires_agreement;
