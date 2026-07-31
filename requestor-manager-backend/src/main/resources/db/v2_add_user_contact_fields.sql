-- PATH: requestor-manager-backend/src/main/resources/db/migration/V2__add_user_contact_fields.sql
-- Migration: Add contact information fields to users table

ALTER TABLE users ADD COLUMN phone VARCHAR(20);
ALTER TABLE users ADD COLUMN address TEXT;

-- Add indexes for potential search optimization (optional, uncomment if needed)
-- CREATE INDEX idx_users_phone ON users(phone);

COMMENT ON COLUMN users.phone IS 'User phone number for contact purposes';
COMMENT ON COLUMN users.address IS 'User address for contact purposes';