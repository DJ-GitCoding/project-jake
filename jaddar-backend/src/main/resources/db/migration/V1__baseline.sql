-- ============================================================================
-- V1 baseline schema — ported from the FastAPI SQLAlchemy models
-- (backend/models/database.py). The DB may already exist from the Python
-- service, so EVERYTHING here is existence-guarded.
-- ============================================================================

-- requeststatus enum (stored as the UPPERCASE name). Guard against re-creation.
DO $$ BEGIN
    CREATE TYPE requeststatus AS ENUM ('PENDING', 'APPROVED', 'DENIED', 'ERROR', 'CANCELLED');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- The Python service may have created the enum with lowercase values originally;
-- ensure all required values are present (idempotent in PG 12+).
ALTER TYPE requeststatus ADD VALUE IF NOT EXISTS 'PENDING';
ALTER TYPE requeststatus ADD VALUE IF NOT EXISTS 'APPROVED';
ALTER TYPE requeststatus ADD VALUE IF NOT EXISTS 'DENIED';
ALTER TYPE requeststatus ADD VALUE IF NOT EXISTS 'ERROR';
ALTER TYPE requeststatus ADD VALUE IF NOT EXISTS 'CANCELLED';

-- ----------------------------------------------------------------------------
-- rdap_request_history
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rdap_request_history (
    id                      BIGSERIAL PRIMARY KEY,
    request_id              VARCHAR(100) NOT NULL UNIQUE,
    user_sub                VARCHAR(255) NOT NULL,
    user_email              VARCHAR(255),
    query_type              VARCHAR(50)  NOT NULL,
    query_value             VARCHAR(255) NOT NULL,
    status                  requeststatus NOT NULL DEFAULT 'PENDING',
    agreements_used         JSONB,
    access_level_requested  INTEGER,
    access_level_granted    INTEGER,
    rdap_data               JSONB,
    error_message           TEXT,
    data_holder_id          VARCHAR(100),
    data_holder_name        VARCHAR(255),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at             TIMESTAMPTZ,
    expires_at              TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS ix_rdap_request_history_request_id ON rdap_request_history (request_id);
CREATE INDEX IF NOT EXISTS ix_rdap_request_history_user_sub   ON rdap_request_history (user_sub);
CREATE INDEX IF NOT EXISTS ix_rdap_request_history_user_email ON rdap_request_history (user_email);

-- ----------------------------------------------------------------------------
-- user_rdap_settings
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_rdap_settings (
    id                        BIGSERIAL PRIMARY KEY,
    user_sub                  VARCHAR(255) NOT NULL UNIQUE,
    user_email                VARCHAR(255),
    default_poll_interval_ms  INTEGER NOT NULL DEFAULT 30000,
    auto_poll_enabled         BOOLEAN NOT NULL DEFAULT true,
    notify_on_approval        BOOLEAN NOT NULL DEFAULT true,
    notify_on_denial          BOOLEAN NOT NULL DEFAULT true,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_user_rdap_settings_user_sub ON user_rdap_settings (user_sub);

-- ----------------------------------------------------------------------------
-- data_holders
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS data_holders (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(255) NOT NULL,
    description    TEXT,
    base_urls      JSONB NOT NULL,
    tlds           JSONB,
    ip_ranges      JSONB,
    asn_ranges     JSONB,
    requires_auth  BOOLEAN NOT NULL DEFAULT false,
    auth_type      VARCHAR(50) NOT NULL DEFAULT 'bearer',
    is_active      BOOLEAN NOT NULL DEFAULT true,
    created_by     VARCHAR(255),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_data_holders_is_active ON data_holders (is_active);

-- ----------------------------------------------------------------------------
-- password_reset_tokens
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id                BIGSERIAL PRIMARY KEY,
    keycloak_user_id  VARCHAR(255) NOT NULL,
    email             VARCHAR(255) NOT NULL,
    token_hash        VARCHAR(128) NOT NULL UNIQUE,
    expires_at        TIMESTAMPTZ NOT NULL,
    used_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_password_reset_tokens_keycloak_user_id ON password_reset_tokens (keycloak_user_id);
CREATE INDEX IF NOT EXISTS ix_password_reset_tokens_email           ON password_reset_tokens (email);
CREATE INDEX IF NOT EXISTS ix_password_reset_tokens_token_hash      ON password_reset_tokens (token_hash);
