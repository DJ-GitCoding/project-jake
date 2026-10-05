-- V20261005__offer_requestor_group_links.sql
-- Whether public (no-agreement) RDAP answers list the joining links of the requestor groups
-- this data holder serves, under a "Public Information Services" notice. Off by default: a
-- data holder opts in.

ALTER TABLE dataholder_config
    ADD COLUMN IF NOT EXISTS offer_requestor_group_links BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN dataholder_config.offer_requestor_group_links IS
    'When true, public RDAP answers list the joining links of the requestor groups this data holder serves';
