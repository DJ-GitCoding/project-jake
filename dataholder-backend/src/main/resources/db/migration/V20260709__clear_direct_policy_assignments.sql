-- V20260709__clear_direct_policy_assignments.sql
-- Direct per-entity policy assignment has been removed from the RDAP Data area.
-- Policies now apply exclusively via scope conditions (and the system default).
-- Clear any existing direct assignments so a previously-pinned policy no longer
-- overrides scope-condition matching (resolvePolicy checks the entity's direct
-- assignment before scope conditions).
--
-- Guarded so a fresh database (where Flyway runs before Hibernate creates tables)
-- does not fail on a missing table/column.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'rdap_entities' AND column_name = 'policy_expression_id'
    ) THEN
        UPDATE rdap_entities
        SET policy_expression_id = NULL
        WHERE policy_expression_id IS NOT NULL;
    END IF;
END $$;
