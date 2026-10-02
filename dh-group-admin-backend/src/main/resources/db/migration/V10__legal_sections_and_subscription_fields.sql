-- Replace the template's single terms_and_conditions and data_usage_policy text with ordered,
-- individually-acceptable legal sections, and add group-defined subscription fields.
DO $$
BEGIN
    IF to_regclass('public.agreement_templates') IS NULL THEN
        RETURN;
    END IF;

    CREATE TABLE IF NOT EXISTS agreement_legal_sections (
        id          BIGSERIAL PRIMARY KEY,
        template_id BIGINT NOT NULL REFERENCES agreement_templates(id) ON DELETE CASCADE,
        title       VARCHAR(200) NOT NULL,
        body        TEXT,
        sort_order  INTEGER DEFAULT 0,
        created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );
    CREATE INDEX IF NOT EXISTS idx_legal_section_template
        ON agreement_legal_sections (template_id);

    CREATE TABLE IF NOT EXISTS template_subscription_fields (
        id               BIGSERIAL PRIMARY KEY,
        template_id      BIGINT NOT NULL REFERENCES agreement_templates(id) ON DELETE CASCADE,
        name             VARCHAR(100) NOT NULL,
        label            VARCHAR(200),
        data_type        VARCHAR(20) NOT NULL DEFAULT 'string',
        required         BOOLEAN NOT NULL DEFAULT false,
        description      TEXT,
        default_value    VARCHAR(255),
        placeholder      VARCHAR(255),
        enum_values      TEXT,
        validation_regex VARCHAR(500),
        min_value        VARCHAR(255),
        max_value        VARCHAR(255),
        max_length       INTEGER,
        sort_order       INTEGER DEFAULT 0,
        created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );
    CREATE INDEX IF NOT EXISTS idx_subscription_field_template
        ON template_subscription_fields (template_id);

    -- Carry the existing text across, once. Guarded on the source column still existing so a
    -- re-run after the drop below is a no-op.
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema='public' AND table_name='agreement_templates'
                 AND column_name='terms_and_conditions') THEN
        INSERT INTO agreement_legal_sections (template_id, title, body, sort_order)
        SELECT t.id, 'Terms and Conditions', t.terms_and_conditions, 0
        FROM agreement_templates t
        WHERE t.terms_and_conditions IS NOT NULL AND btrim(t.terms_and_conditions) <> ''
          AND NOT EXISTS (SELECT 1 FROM agreement_legal_sections s
                          WHERE s.template_id = t.id AND s.title = 'Terms and Conditions');
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema='public' AND table_name='agreement_templates'
                 AND column_name='data_usage_policy') THEN
        INSERT INTO agreement_legal_sections (template_id, title, body, sort_order)
        SELECT t.id, 'Data Usage Policy', t.data_usage_policy, 1
        FROM agreement_templates t
        WHERE t.data_usage_policy IS NOT NULL AND btrim(t.data_usage_policy) <> ''
          AND NOT EXISTS (SELECT 1 FROM agreement_legal_sections s
                          WHERE s.template_id = t.id AND s.title = 'Data Usage Policy');
    END IF;

    ALTER TABLE agreement_templates DROP COLUMN IF EXISTS terms_and_conditions;
    ALTER TABLE agreement_templates DROP COLUMN IF EXISTS data_usage_policy;

    IF to_regclass('public.agreement_subscriptions') IS NOT NULL THEN
        ALTER TABLE agreement_subscriptions
            ADD COLUMN IF NOT EXISTS subscription_field_values JSONB;
        ALTER TABLE agreement_subscriptions
            ADD COLUMN IF NOT EXISTS accepted_terms JSONB;
    END IF;
END $$;
