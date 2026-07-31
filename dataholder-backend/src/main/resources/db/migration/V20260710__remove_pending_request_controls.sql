-- V20260710__remove_pending_request_controls.sql
-- The per-entity "Pending Request Control" feature (simulated delays / forced
-- responses, assigned in the RDAP Data area) has been removed entirely.
-- Drop its foreign-key columns from every referencing table, then drop the table.
--
-- Guarded with ALTER TABLE IF EXISTS / DROP ... IF EXISTS so this is a safe no-op
-- on a fresh database (Flyway runs before Hibernate creates tables). Dropping the
-- FK columns first removes the constraints; CASCADE on the table drop is a
-- belt-and-suspenders backstop.

ALTER TABLE IF EXISTS rdap_entities  DROP COLUMN IF EXISTS pending_request_control_id;
ALTER TABLE IF EXISTS rdap_domains   DROP COLUMN IF EXISTS pending_request_control_id;
ALTER TABLE IF EXISTS rdap_ips       DROP COLUMN IF EXISTS pending_request_control_id;
ALTER TABLE IF EXISTS rdap_asns      DROP COLUMN IF EXISTS pending_request_control_id;
ALTER TABLE IF EXISTS pending_requests DROP COLUMN IF EXISTS control_id;

DROP TABLE IF EXISTS pending_request_controls CASCADE;
