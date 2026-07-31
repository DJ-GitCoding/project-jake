-- Split the requestor's single `requestor_full_name` column into first/last name.
-- The application now captures First Name and Last Name separately (autofilled from the requestor's
-- profile) and carries them across the RM -> DHG -> DH pipeline. Existing rows are backfilled by
-- splitting the legacy value on the first space (first token -> first name, remainder -> last name).

ALTER TABLE agreement_subscriptions ADD COLUMN IF NOT EXISTS requestor_first_name VARCHAR(255);
ALTER TABLE agreement_subscriptions ADD COLUMN IF NOT EXISTS requestor_last_name VARCHAR(255);

UPDATE agreement_subscriptions
SET requestor_first_name = split_part(trim(requestor_full_name), ' ', 1),
    requestor_last_name = CASE
        WHEN position(' ' in trim(requestor_full_name)) > 0
        THEN trim(substring(trim(requestor_full_name) from position(' ' in trim(requestor_full_name)) + 1))
        ELSE '' END
WHERE requestor_full_name IS NOT NULL AND trim(requestor_full_name) <> '';

ALTER TABLE agreement_subscriptions DROP COLUMN IF EXISTS requestor_full_name;
