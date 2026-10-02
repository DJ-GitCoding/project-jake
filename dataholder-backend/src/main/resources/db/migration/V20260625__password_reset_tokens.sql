-- Single-use password reset tokens for dataholder_users.
-- Only the SHA-256 hash of the token is stored; the raw token is emailed to the user.
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    token_hash  VARCHAR(128) NOT NULL,
    expires_at  TIMESTAMP    NOT NULL,
    used_at     TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT fk_prt_user FOREIGN KEY (user_id)
        REFERENCES dataholder_users (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_prt_token_hash ON password_reset_tokens (token_hash);
CREATE INDEX IF NOT EXISTS idx_prt_user_id    ON password_reset_tokens (user_id);
