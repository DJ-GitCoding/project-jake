/*
 * Subscription fields lost their separate label: the field name is what the requestor sees,
 * so a second display string was only ever a way for the two to drift apart.
 *
 * Guarded on the table existing — Flyway runs before Hibernate creates the schema on a
 * fresh database, where there is nothing to drop.
 */
DO $$
BEGIN
    IF to_regclass('public.template_subscription_fields') IS NOT NULL THEN
        ALTER TABLE template_subscription_fields DROP COLUMN IF EXISTS label;
    END IF;
END $$;
