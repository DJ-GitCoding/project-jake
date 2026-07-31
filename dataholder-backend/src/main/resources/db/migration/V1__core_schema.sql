-- V1__core_schema.sql
-- Consolidated schema: all tables in their final state
-- Includes: type_code on agreement_request_types, requestor_group_code on agreement_subscriptions

-- =====================================================
-- RDAP Legacy Domain Data (kept for migration functions)
-- =====================================================
CREATE TABLE rdap_domains (
    id BIGSERIAL PRIMARY KEY,
    ldh_name VARCHAR(255) NOT NULL UNIQUE,
    handle VARCHAR(100),
    status TEXT[],
    registration_date TIMESTAMP,
    last_changed_date TIMESTAMP,
    expiration_date TIMESTAMP,
    nameservers JSONB DEFAULT '[]',
    dnssec_enabled BOOLEAN DEFAULT FALSE,
    rdap_data_level_0 JSONB NOT NULL,
    rdap_data_level_1 JSONB NOT NULL,
    rdap_data_level_2 JSONB NOT NULL,
    rdap_data_level_3 JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    pending_request_control_id BIGINT,
    test_data_flag_id BIGINT,
    policy_expression_id BIGINT
);
CREATE INDEX idx_rdap_domains_ldh_name ON rdap_domains(ldh_name);

-- =====================================================
-- RDAP Legacy IP Data
-- =====================================================
CREATE TABLE rdap_ips (
    id BIGSERIAL PRIMARY KEY,
    handle VARCHAR(100) NOT NULL UNIQUE,
    start_address VARCHAR(50) NOT NULL,
    end_address VARCHAR(50) NOT NULL,
    ip_version VARCHAR(10) NOT NULL,
    name VARCHAR(255),
    country VARCHAR(10),
    registration_date TIMESTAMP,
    last_changed_date TIMESTAMP,
    rdap_data_level_0 JSONB NOT NULL,
    rdap_data_level_1 JSONB NOT NULL,
    rdap_data_level_2 JSONB NOT NULL,
    rdap_data_level_3 JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    pending_request_control_id BIGINT,
    test_data_flag_id BIGINT,
    policy_expression_id BIGINT
);
CREATE INDEX idx_rdap_ips_handle ON rdap_ips(handle);
CREATE INDEX idx_rdap_ips_start_address ON rdap_ips(start_address);

-- =====================================================
-- RDAP Legacy ASN Data
-- =====================================================
CREATE TABLE rdap_asns (
    id BIGSERIAL PRIMARY KEY,
    handle VARCHAR(50) NOT NULL UNIQUE,
    start_autnum INTEGER NOT NULL,
    end_autnum INTEGER NOT NULL,
    name VARCHAR(255),
    country VARCHAR(10),
    registration_date TIMESTAMP,
    last_changed_date TIMESTAMP,
    rdap_data_level_0 JSONB NOT NULL,
    rdap_data_level_1 JSONB NOT NULL,
    rdap_data_level_2 JSONB NOT NULL,
    rdap_data_level_3 JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    pending_request_control_id BIGINT,
    test_data_flag_id BIGINT,
    policy_expression_id BIGINT
);
CREATE INDEX idx_rdap_asns_handle ON rdap_asns(handle);
CREATE INDEX idx_rdap_asns_start_autnum ON rdap_asns(start_autnum);

-- =====================================================
-- Access Policies (legacy, kept for migration reference)
-- =====================================================
CREATE TABLE access_policies (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    default_access_level INTEGER NOT NULL DEFAULT 1 CHECK (default_access_level BETWEEN 0 AND 3),
    requires_manual_verification BOOLEAN DEFAULT FALSE,
    manual_verification_threshold INTEGER DEFAULT 3 CHECK (manual_verification_threshold BETWEEN 0 AND 3),
    requires_agreement BOOLEAN DEFAULT TRUE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- Pending Request Controls
-- =====================================================
CREATE TABLE pending_request_controls (
    id BIGSERIAL PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT true,
    wait_time_ms BIGINT DEFAULT 0,
    default_response VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    denial_reason TEXT,
    admin_notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_default_response CHECK (default_response IN ('PENDING', 'APPROVED', 'DENIED'))
);
CREATE INDEX idx_prc_enabled ON pending_request_controls(enabled);
CREATE INDEX idx_prc_default_response ON pending_request_controls(default_response);

-- Add FK constraints to legacy RDAP tables
ALTER TABLE rdap_domains ADD CONSTRAINT fk_rdap_domains_prc
    FOREIGN KEY (pending_request_control_id) REFERENCES pending_request_controls(id) ON DELETE SET NULL;
ALTER TABLE rdap_ips ADD CONSTRAINT fk_rdap_ips_prc
    FOREIGN KEY (pending_request_control_id) REFERENCES pending_request_controls(id) ON DELETE SET NULL;
ALTER TABLE rdap_asns ADD CONSTRAINT fk_rdap_asns_prc
    FOREIGN KEY (pending_request_control_id) REFERENCES pending_request_controls(id) ON DELETE SET NULL;
CREATE INDEX idx_rdap_domains_prc ON rdap_domains(pending_request_control_id);
CREATE INDEX idx_rdap_ips_prc ON rdap_ips(pending_request_control_id);
CREATE INDEX idx_rdap_asns_prc ON rdap_asns(pending_request_control_id);

-- =====================================================
-- Test Data Flags
-- =====================================================
CREATE TABLE test_data_flags (
    id BIGSERIAL PRIMARY KEY,
    is_test_data BOOLEAN NOT NULL DEFAULT false,
    description TEXT,
    test_label VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_tdf_is_test_data ON test_data_flags(is_test_data);
CREATE INDEX idx_tdf_test_label ON test_data_flags(test_label);

ALTER TABLE rdap_domains ADD CONSTRAINT fk_rdap_domains_tdf
    FOREIGN KEY (test_data_flag_id) REFERENCES test_data_flags(id) ON DELETE SET NULL;
ALTER TABLE rdap_ips ADD CONSTRAINT fk_rdap_ips_tdf
    FOREIGN KEY (test_data_flag_id) REFERENCES test_data_flags(id) ON DELETE SET NULL;
ALTER TABLE rdap_asns ADD CONSTRAINT fk_rdap_asns_tdf
    FOREIGN KEY (test_data_flag_id) REFERENCES test_data_flags(id) ON DELETE SET NULL;
CREATE INDEX idx_rdap_domains_tdf ON rdap_domains(test_data_flag_id);
CREATE INDEX idx_rdap_ips_tdf ON rdap_ips(test_data_flag_id);
CREATE INDEX idx_rdap_asns_tdf ON rdap_asns(test_data_flag_id);

-- =====================================================
-- Pending Requests (for manual verification)
-- =====================================================
CREATE TABLE pending_requests (
    id BIGSERIAL PRIMARY KEY,
    request_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    query_type VARCHAR(20) NOT NULL,
    query_value VARCHAR(255) NOT NULL,
    requested_access_level INTEGER NOT NULL,
    requestor_sub VARCHAR(255),
    requestor_username VARCHAR(255),
    requestor_email VARCHAR(255),
    requestor_groups TEXT[],
    requestor_ip VARCHAR(255),
    agreement_names TEXT[],
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    admin_notes TEXT,
    reviewed_by VARCHAR(255),
    reviewed_at TIMESTAMP,
    response_data JSONB,
    expires_at TIMESTAMP NOT NULL,
    auto_approved BOOLEAN DEFAULT FALSE,
    denial_reason TEXT,
    control_id BIGINT,
    wait_time_applied_ms BIGINT,
    response_time_ms INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_pending_requests_request_id ON pending_requests(request_id);
CREATE INDEX idx_pending_requests_status ON pending_requests(status);
CREATE INDEX idx_pending_requests_expires_at ON pending_requests(expires_at);
CREATE INDEX idx_pending_requests_control_id ON pending_requests(control_id);

-- =====================================================
-- Request Audit Log
-- =====================================================
CREATE TABLE request_audit_log (
    id BIGSERIAL PRIMARY KEY,
    query_type VARCHAR(20) NOT NULL,
    query_value VARCHAR(255) NOT NULL,
    requestor_sub VARCHAR(255),
    requestor_username VARCHAR(255),
    requestor_ip VARCHAR(50),
    agreement_names TEXT[],
    access_level_requested INTEGER,
    access_level_granted INTEGER,
    result VARCHAR(20) NOT NULL,
    result_message TEXT,
    request_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    response_time_ms INTEGER
);
CREATE INDEX idx_request_audit_log_timestamp ON request_audit_log(request_timestamp);
CREATE INDEX idx_request_audit_log_requestor_sub ON request_audit_log(requestor_sub);

-- =====================================================
-- Dataholder Users (admin UI)
-- =====================================================
CREATE TABLE dataholder_users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    full_name VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'ADMIN',
    is_active BOOLEAN NOT NULL DEFAULT true,
    last_login TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50)
);
CREATE INDEX idx_dataholder_users_username ON dataholder_users(username);
CREATE INDEX idx_dataholder_users_email ON dataholder_users(email);

-- =====================================================
-- Agreements (standalone, no requestor_groups dependency)
-- =====================================================
CREATE TABLE agreements (
    id BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(255) UNIQUE,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    access_level INTEGER NOT NULL DEFAULT 0,
    max_sensitivity_level INTEGER NOT NULL DEFAULT 0,
    rdap_parameters_id BIGINT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    effective_from TIMESTAMP,
    effective_to TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    CONSTRAINT chk_agreements_max_sensitivity_level CHECK (max_sensitivity_level >= 0 AND max_sensitivity_level <= 3)
);
CREATE INDEX idx_agreements_name ON agreements(name);
CREATE INDEX idx_agreements_external_id ON agreements(external_id);
CREATE INDEX idx_agreements_access_level ON agreements(access_level);
CREATE INDEX idx_agreements_is_active ON agreements(is_active);

-- =====================================================
-- Agreement Access Levels (element collection)
-- =====================================================
CREATE TABLE agreement_access_levels (
    agreement_id BIGINT NOT NULL,
    access_level INTEGER,
    CONSTRAINT fk_agreement_access_levels_agreement
        FOREIGN KEY (agreement_id) REFERENCES agreements(id) ON DELETE CASCADE
);
CREATE INDEX idx_agreement_access_levels_agreement ON agreement_access_levels(agreement_id);

-- =====================================================
-- RDAP Parameters
-- =====================================================
CREATE TABLE agreement_rdap_parameters (
    id BIGSERIAL PRIMARY KEY,
    -- Domain fields
    domain_handle BOOLEAN DEFAULT true,
    domain_name BOOLEAN DEFAULT true,
    domain_status BOOLEAN DEFAULT true,
    domain_port43 BOOLEAN DEFAULT true,
    domain_public_ids BOOLEAN DEFAULT true,
    -- Nameserver fields
    nameservers BOOLEAN DEFAULT true,
    nameserver_handle BOOLEAN DEFAULT true,
    nameserver_name BOOLEAN DEFAULT true,
    nameserver_ip_addresses BOOLEAN DEFAULT true,
    nameserver_status BOOLEAN DEFAULT true,
    -- Event fields
    events BOOLEAN DEFAULT true,
    event_registration BOOLEAN DEFAULT true,
    event_expiration BOOLEAN DEFAULT true,
    event_last_changed BOOLEAN DEFAULT true,
    event_last_update_of_rdap_db BOOLEAN DEFAULT true,
    event_transfer BOOLEAN DEFAULT true,
    -- Registrant fields
    registrant_entity BOOLEAN DEFAULT true,
    registrant_handle BOOLEAN DEFAULT true,
    registrant_name BOOLEAN DEFAULT true,
    registrant_organization BOOLEAN DEFAULT true,
    registrant_email BOOLEAN DEFAULT true,
    registrant_phone BOOLEAN DEFAULT true,
    registrant_fax BOOLEAN DEFAULT true,
    registrant_address BOOLEAN DEFAULT true,
    registrant_street BOOLEAN DEFAULT true,
    registrant_city BOOLEAN DEFAULT true,
    registrant_state_province BOOLEAN DEFAULT true,
    registrant_postal_code BOOLEAN DEFAULT true,
    registrant_country BOOLEAN DEFAULT true,
    -- Admin fields
    admin_entity BOOLEAN DEFAULT true,
    admin_handle BOOLEAN DEFAULT true,
    admin_name BOOLEAN DEFAULT true,
    admin_organization BOOLEAN DEFAULT true,
    admin_email BOOLEAN DEFAULT true,
    admin_phone BOOLEAN DEFAULT true,
    admin_fax BOOLEAN DEFAULT true,
    admin_address BOOLEAN DEFAULT true,
    admin_street BOOLEAN DEFAULT true,
    admin_city BOOLEAN DEFAULT true,
    admin_state_province BOOLEAN DEFAULT true,
    admin_postal_code BOOLEAN DEFAULT true,
    admin_country BOOLEAN DEFAULT true,
    -- Tech fields
    tech_entity BOOLEAN DEFAULT true,
    tech_handle BOOLEAN DEFAULT true,
    tech_name BOOLEAN DEFAULT true,
    tech_organization BOOLEAN DEFAULT true,
    tech_email BOOLEAN DEFAULT true,
    tech_phone BOOLEAN DEFAULT true,
    tech_fax BOOLEAN DEFAULT true,
    tech_address BOOLEAN DEFAULT true,
    tech_street BOOLEAN DEFAULT true,
    tech_city BOOLEAN DEFAULT true,
    tech_state_province BOOLEAN DEFAULT true,
    tech_postal_code BOOLEAN DEFAULT true,
    tech_country BOOLEAN DEFAULT true,
    -- Billing fields
    billing_entity BOOLEAN DEFAULT true,
    billing_handle BOOLEAN DEFAULT true,
    billing_name BOOLEAN DEFAULT true,
    billing_organization BOOLEAN DEFAULT true,
    billing_email BOOLEAN DEFAULT true,
    billing_phone BOOLEAN DEFAULT true,
    billing_fax BOOLEAN DEFAULT true,
    billing_address BOOLEAN DEFAULT true,
    billing_street BOOLEAN DEFAULT true,
    billing_city BOOLEAN DEFAULT true,
    billing_state_province BOOLEAN DEFAULT true,
    billing_postal_code BOOLEAN DEFAULT true,
    billing_country BOOLEAN DEFAULT true,
    -- Registrar fields
    registrar_entity BOOLEAN DEFAULT true,
    registrar_handle BOOLEAN DEFAULT true,
    registrar_name BOOLEAN DEFAULT true,
    registrar_email BOOLEAN DEFAULT true,
    registrar_phone BOOLEAN DEFAULT true,
    registrar_url BOOLEAN DEFAULT true,
    registrar_abuse_contact BOOLEAN DEFAULT true,
    -- DNSSEC fields
    dnssec_data BOOLEAN DEFAULT true,
    dnssec_delegation_signed BOOLEAN DEFAULT true,
    dnssec_ds_data BOOLEAN DEFAULT true,
    dnssec_key_data BOOLEAN DEFAULT true,
    -- Network fields
    network_handle BOOLEAN DEFAULT true,
    network_name BOOLEAN DEFAULT true,
    network_type BOOLEAN DEFAULT true,
    network_start_address BOOLEAN DEFAULT true,
    network_end_address BOOLEAN DEFAULT true,
    network_ip_version BOOLEAN DEFAULT true,
    network_parent_handle BOOLEAN DEFAULT true,
    network_cidr BOOLEAN DEFAULT true,
    network_country BOOLEAN DEFAULT true,
    -- ASN fields
    autnum_handle BOOLEAN DEFAULT true,
    autnum_start BOOLEAN DEFAULT true,
    autnum_end BOOLEAN DEFAULT true,
    autnum_name BOOLEAN DEFAULT true,
    autnum_type BOOLEAN DEFAULT true,
    autnum_country BOOLEAN DEFAULT true,
    -- Other
    links BOOLEAN DEFAULT true,
    notices BOOLEAN DEFAULT true,
    remarks BOOLEAN DEFAULT true,
    -- Metadata
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_rdap_params_created_at ON agreement_rdap_parameters(created_at);

-- Add FK from agreements to rdap_parameters
ALTER TABLE agreements ADD CONSTRAINT fk_agreements_rdap_parameters
    FOREIGN KEY (rdap_parameters_id) REFERENCES agreement_rdap_parameters(id) ON DELETE SET NULL;
CREATE INDEX idx_agreements_rdap_parameters ON agreements(rdap_parameters_id);

-- =====================================================
-- Agreement Templates
-- =====================================================
CREATE TABLE agreement_templates (
    id BIGSERIAL PRIMARY KEY,
    template_id VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    short_description VARCHAR(25),
    access_level INTEGER NOT NULL DEFAULT 0,
    required_group_types VARCHAR(500),
    terms_and_conditions TEXT,
    data_usage_policy TEXT,
    max_queries_per_day INTEGER,
    max_queries_per_month INTEGER,
    requires_manual_approval BOOLEAN NOT NULL DEFAULT true,
    is_published BOOLEAN NOT NULL DEFAULT false,
    rdap_parameters_id BIGINT REFERENCES agreement_rdap_parameters(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255)
);
CREATE INDEX idx_agreement_templates_published ON agreement_templates(is_published);
CREATE INDEX idx_agreement_templates_template_id ON agreement_templates(template_id);
CREATE INDEX idx_agreement_templates_rdap_parameters ON agreement_templates(rdap_parameters_id);

-- =====================================================
-- Agreement Request Types (per-template request type configurations)
-- =====================================================
CREATE TABLE agreement_request_types (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL REFERENCES agreement_templates(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    type_code INTEGER,
    description TEXT,
    access_level INTEGER NOT NULL DEFAULT 0,
    supports_confidential BOOLEAN NOT NULL DEFAULT false,
    supports_exigent BOOLEAN NOT NULL DEFAULT false,
    rdap_parameters_id BIGINT REFERENCES agreement_rdap_parameters(id) ON DELETE SET NULL,
    sort_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_request_type_template ON agreement_request_types(template_id);
CREATE INDEX idx_request_type_name ON agreement_request_types(name);
CREATE INDEX idx_request_type_active ON agreement_request_types(is_active);
CREATE INDEX idx_request_type_rdap_params ON agreement_request_types(rdap_parameters_id);

-- =====================================================
-- Agreement Requests (incoming from Requestor Manager)
-- =====================================================
CREATE TABLE agreement_requests (
    id BIGSERIAL PRIMARY KEY,
    request_id VARCHAR(100) NOT NULL UNIQUE,
    template_id BIGINT REFERENCES agreement_templates(id),
    requestor_group_id VARCHAR(255) NOT NULL,
    requestor_group_name VARCHAR(255) NOT NULL,
    requestor_organization VARCHAR(255),
    requestor_contact_email VARCHAR(255),
    requestor_group_type VARCHAR(100),
    requestor_description TEXT,
    requestor_agent_id VARCHAR(255),
    requestor_agent_url VARCHAR(500),
    requestor_agent_callback_url VARCHAR(500),
    requested_access_level INTEGER,
    purpose TEXT,
    additional_terms TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    status_message TEXT,
    test_started_at TIMESTAMP,
    test_completed_at TIMESTAMP,
    test_result VARCHAR(50),
    test_details TEXT,
    reviewed_by VARCHAR(255),
    reviewed_at TIMESTAMP,
    review_notes TEXT,
    agreement_id BIGINT REFERENCES agreements(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP
);
CREATE INDEX idx_agreement_requests_status ON agreement_requests(status);
CREATE INDEX idx_agreement_requests_request_id ON agreement_requests(request_id);
CREATE INDEX idx_agreement_requests_requestor_group ON agreement_requests(requestor_group_id);

-- =====================================================
-- Agreement Subscriptions
-- =====================================================
CREATE TABLE agreement_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    request_id VARCHAR(255) NOT NULL UNIQUE,
    template_id BIGINT NOT NULL REFERENCES agreement_templates(id),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    status_message TEXT,
    status_changed_at TIMESTAMP,
    requestor_group_id VARCHAR(255) NOT NULL,
    requestor_group_name VARCHAR(255) NOT NULL,
    requestor_group_code VARCHAR(10),
    requestor_group_type VARCHAR(255),
    requestor_description TEXT,
    requestor_full_name VARCHAR(255),
    requestor_organization VARCHAR(255),
    requestor_contact_email VARCHAR(255),
    requestor_phone VARCHAR(255),
    requestor_address VARCHAR(255),
    requestor_city VARCHAR(255),
    requestor_state_province VARCHAR(255),
    requestor_postal_code VARCHAR(255),
    requestor_country VARCHAR(255),
    requestor_agent_id VARCHAR(255),
    requestor_agent_url VARCHAR(255),
    requestor_agent_callback_url VARCHAR(255),
    access_level INTEGER DEFAULT 0,
    granted_sensitivity_level INTEGER,
    purpose TEXT,
    additional_terms TEXT,
    rdap_parameters_id BIGINT REFERENCES agreement_rdap_parameters(id),
    test_started_at TIMESTAMP,
    test_completed_at TIMESTAMP,
    test_result VARCHAR(255),
    test_details TEXT,
    reviewed_by VARCHAR(255),
    reviewed_at TIMESTAMP,
    review_notes TEXT,
    effective_from TIMESTAMP,
    effective_to TIMESTAMP,
    request_expires_at TIMESTAMP,
    max_queries_per_day INTEGER,
    max_queries_per_month INTEGER,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    activated_at TIMESTAMP,
    activated_by VARCHAR(255)
);
CREATE INDEX idx_subscription_request_id ON agreement_subscriptions(request_id);
CREATE INDEX idx_subscription_requestor_group ON agreement_subscriptions(requestor_group_id);
CREATE INDEX idx_subscription_status ON agreement_subscriptions(status);

-- =====================================================
-- Agreement Status Logs (audit trail)
-- =====================================================
CREATE TABLE agreement_status_logs (
    id BIGSERIAL PRIMARY KEY,
    agreement_request_id BIGINT REFERENCES agreement_requests(id),
    subscription_id BIGINT REFERENCES agreement_subscriptions(id),
    previous_status VARCHAR(50),
    new_status VARCHAR(50) NOT NULL,
    changed_by VARCHAR(255),
    change_reason TEXT,
    source VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_agreement_status_logs_request ON agreement_status_logs(agreement_request_id);
CREATE INDEX idx_agreement_status_logs_subscription_id ON agreement_status_logs(subscription_id);
COMMENT ON COLUMN agreement_status_logs.subscription_id IS 'Reference to the subscription for subscription-related status changes';

-- =====================================================
-- Policy Expressions
-- =====================================================
CREATE TABLE policy_expressions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    default_access_level INTEGER NOT NULL DEFAULT 1,
    legal_status VARCHAR(50) NOT NULL DEFAULT 'NO_DECISION',
    protected_status VARCHAR(50) NOT NULL DEFAULT 'NO_DECISION',
    requires_manual_verification BOOLEAN DEFAULT FALSE,
    manual_verification_threshold INTEGER DEFAULT 4,
    requires_agreement BOOLEAN DEFAULT TRUE,
    is_active BOOLEAN DEFAULT TRUE,
    is_default BOOLEAN DEFAULT FALSE,
    priority INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_policy_expressions_active ON policy_expressions(is_active);
CREATE INDEX idx_policy_expressions_default ON policy_expressions(is_default);
CREATE INDEX idx_policy_expressions_priority ON policy_expressions(priority DESC);
CREATE INDEX idx_policy_expressions_legal_status ON policy_expressions(legal_status);
CREATE INDEX idx_policy_expressions_protected_status ON policy_expressions(protected_status);
CREATE UNIQUE INDEX idx_policy_expressions_single_default ON policy_expressions(is_default) WHERE is_default = TRUE;
COMMENT ON COLUMN policy_expressions.legal_status IS 'Legal status values: NO_DECISION, UNKNOWN, NATURAL, NATURAL_UNKNOWN, LEGAL, LEGAL_UNKNOWN, ANY';
COMMENT ON COLUMN policy_expressions.protected_status IS 'Protected status values: NO_DECISION, UNKNOWN, NORMAL, NORMAL_UNKNOWN, PROTECTED, PROTECTED_UNKNOWN, ANY';

-- =====================================================
-- Policy Redaction Rules
-- =====================================================
CREATE TABLE policy_redaction_rules (
    id BIGSERIAL PRIMARY KEY,
    policy_expression_id BIGINT NOT NULL,
    object_type VARCHAR(50) NOT NULL DEFAULT 'ALL',
    field_path VARCHAR(255) NOT NULL,
    field_display_name VARCHAR(255),
    redaction_action VARCHAR(50) NOT NULL DEFAULT 'SHOW',
    min_access_level INTEGER NOT NULL DEFAULT 0,
    replacement_value TEXT,
    description TEXT,
    rule_order INTEGER DEFAULT 0,
    is_enabled BOOLEAN DEFAULT TRUE,
    CONSTRAINT fk_policy_redaction_rules_policy
        FOREIGN KEY (policy_expression_id) REFERENCES policy_expressions(id) ON DELETE CASCADE,
    CONSTRAINT uq_policy_redaction_rules_field
        UNIQUE (policy_expression_id, object_type, field_path)
);
CREATE INDEX idx_redaction_rules_policy_id ON policy_redaction_rules(policy_expression_id);
CREATE INDEX idx_redaction_rules_object_type ON policy_redaction_rules(object_type);
CREATE INDEX idx_redaction_rules_enabled ON policy_redaction_rules(is_enabled);
CREATE INDEX idx_redaction_rules_order ON policy_redaction_rules(policy_expression_id, rule_order);
COMMENT ON COLUMN policy_redaction_rules.object_type IS 'RDAP object types: DOMAIN, ENTITY, NAMESERVER, IP_NETWORK, AUTNUM, ALL';
COMMENT ON COLUMN policy_redaction_rules.redaction_action IS 'Actions: SHOW, HIDE, REDACT, REPLACE, PARTIAL, HASH';
COMMENT ON COLUMN policy_redaction_rules.min_access_level IS 'Minimum access level (0-4) required to see field unredacted';
COMMENT ON COLUMN policy_redaction_rules.field_path IS 'Dot-notation path to field, e.g., "vcardArray.email" or "entities.roles"';

-- Add policy_expression FK to legacy RDAP tables
ALTER TABLE rdap_domains ADD CONSTRAINT fk_rdap_domains_policy_expression
    FOREIGN KEY (policy_expression_id) REFERENCES policy_expressions(id) ON DELETE SET NULL;
ALTER TABLE rdap_ips ADD CONSTRAINT fk_rdap_ips_policy_expression
    FOREIGN KEY (policy_expression_id) REFERENCES policy_expressions(id) ON DELETE SET NULL;
ALTER TABLE rdap_asns ADD CONSTRAINT fk_rdap_asns_policy_expression
    FOREIGN KEY (policy_expression_id) REFERENCES policy_expressions(id) ON DELETE SET NULL;
CREATE INDEX idx_rdap_domains_policy_expr ON rdap_domains(policy_expression_id);
CREATE INDEX idx_rdap_ips_policy_expr ON rdap_ips(policy_expression_id);
CREATE INDEX idx_rdap_asns_policy_expr ON rdap_asns(policy_expression_id);

-- =====================================================
-- Redaction Rules (element-level redaction configuration)
-- =====================================================
CREATE TABLE redaction_rules (
    id BIGSERIAL PRIMARY KEY,
    element_path VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255),
    element_category VARCHAR(50),
    description TEXT,
    sensitivity_level INTEGER NOT NULL DEFAULT 0,
    redaction_behavior VARCHAR(50) NOT NULL DEFAULT 'OK',
    is_active BOOLEAN NOT NULL DEFAULT true,
    is_system_rule BOOLEAN NOT NULL DEFAULT false,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_redaction_rules_category ON redaction_rules(element_category);
CREATE INDEX idx_redaction_rules_active ON redaction_rules(is_active);