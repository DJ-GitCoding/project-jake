-- Give every agreement template the opaque code a data holder names it by, alongside the
-- per-request-type codes V9 introduced. Same shape: 16 hex characters of a salted sha256.
DO $$
DECLARE
    remaining INTEGER;
    guard     INTEGER := 0;
BEGIN
    IF to_regclass('public.agreement_templates') IS NULL THEN
        -- Fresh database: Hibernate creates the column, and the app mints codes on save.
        RETURN;
    END IF;

    ALTER TABLE agreement_templates
        ADD COLUMN IF NOT EXISTS agreement_code VARCHAR(64);

    UPDATE agreement_templates t
    SET agreement_code = LEFT(
            encode(
                sha256(convert_to(
                    COALESCE(t.template_id, t.id::text)
                        || ':' || COALESCE(t.name, '')
                        || ':' || gen_random_uuid()::text,
                    'UTF8')),
                'hex'),
            16)
    WHERE t.agreement_code IS NULL OR t.agreement_code !~ '^[0-9a-f]{16}$';

    -- A collision is vanishingly unlikely, but a unique index will not be built over one.
    LOOP
        UPDATE agreement_templates t
        SET agreement_code = LEFT(
                encode(
                    sha256(convert_to(
                        COALESCE(t.template_id, t.id::text)
                            || ':' || COALESCE(t.name, '')
                            || ':' || gen_random_uuid()::text,
                        'UTF8')),
                    'hex'),
                16)
        WHERE t.id IN (
            SELECT id FROM (
                SELECT id, row_number() OVER (PARTITION BY agreement_code ORDER BY id) AS rn
                FROM agreement_templates
            ) ranked WHERE ranked.rn > 1
        );
        GET DIAGNOSTICS remaining = ROW_COUNT;
        guard := guard + 1;
        EXIT WHEN remaining = 0 OR guard >= 10;
    END LOOP;

    IF remaining > 0 THEN
        RAISE EXCEPTION 'Could not resolve duplicate agreement codes after % passes', guard;
    END IF;

    CREATE UNIQUE INDEX IF NOT EXISTS ux_agreement_templates_agreement_code
        ON agreement_templates (agreement_code);
END $$;
