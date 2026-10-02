-- Replace the integer agreement_request_types.type_code with a salted 16 hex character hash.
-- Dated to match this service's existing migration naming.
DO $$
DECLARE
    remaining INTEGER;
    guard     INTEGER := 0;
BEGIN
    IF to_regclass('public.agreement_request_types') IS NULL THEN
        RETURN;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'agreement_request_types'
          AND column_name = 'type_code'
          AND data_type <> 'character varying'
    ) THEN
        ALTER TABLE agreement_request_types
            ALTER COLUMN type_code TYPE VARCHAR(64) USING type_code::text;
    END IF;

    ALTER TABLE agreement_request_types
        ADD COLUMN IF NOT EXISTS type_code VARCHAR(64);

    UPDATE agreement_request_types rt
    SET type_code = LEFT(
            encode(
                sha256(convert_to(
                    COALESCE(t.template_id, rt.template_id::text)
                        || ':' || COALESCE(rt.name, '')
                        || ':' || gen_random_uuid()::text,
                    'UTF8')),
                'hex'),
            16)
    FROM agreement_templates t
    WHERE t.id = rt.template_id
      AND (rt.type_code IS NULL OR rt.type_code !~ '^[0-9a-f]{16}$');

    UPDATE agreement_request_types rt
    SET type_code = LEFT(
            encode(
                sha256(convert_to(
                    rt.template_id::text || ':' || COALESCE(rt.name, '') || ':' || gen_random_uuid()::text,
                    'UTF8')),
                'hex'),
            16)
    WHERE rt.type_code IS NULL OR rt.type_code !~ '^[0-9a-f]{16}$';

    LOOP
        UPDATE agreement_request_types rt
        SET type_code = LEFT(
                encode(
                    sha256(convert_to(
                        rt.template_id::text || ':' || COALESCE(rt.name, '') || ':' || gen_random_uuid()::text,
                        'UTF8')),
                    'hex'),
                16)
        WHERE rt.id IN (
            SELECT id FROM (
                SELECT id, row_number() OVER (PARTITION BY type_code ORDER BY id) AS rn
                FROM agreement_request_types
            ) ranked WHERE ranked.rn > 1
        );
        GET DIAGNOSTICS remaining = ROW_COUNT;
        guard := guard + 1;
        EXIT WHEN remaining = 0 OR guard >= 10;
    END LOOP;

    IF remaining > 0 THEN
        RAISE EXCEPTION 'Could not resolve duplicate request type codes after % passes', guard;
    END IF;

    CREATE UNIQUE INDEX IF NOT EXISTS ux_request_type_code
        ON agreement_request_types (type_code);
END $$;
