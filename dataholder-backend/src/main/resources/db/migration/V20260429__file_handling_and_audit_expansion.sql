-- =====================================================================
-- Migration: File handling, automation rules, and expanded audit logging
-- =====================================================================

-- 0) Add custom_params JSONB column to pending_requests for storing
--    custom parameters passed with RDAP queries (file references, etc.)
ALTER TABLE pending_requests ADD COLUMN IF NOT EXISTS custom_params JSONB;

-- 1) Expand request_audit_log with event classification and file/security fields
ALTER TABLE request_audit_log ADD COLUMN IF NOT EXISTS event_type VARCHAR(25) DEFAULT 'RDAP_QUERY';
ALTER TABLE request_audit_log ADD COLUMN IF NOT EXISTS severity VARCHAR(10) DEFAULT 'INFO';
ALTER TABLE request_audit_log ADD COLUMN IF NOT EXISTS threat_type VARCHAR(40);
ALTER TABLE request_audit_log ADD COLUMN IF NOT EXISTS original_filename VARCHAR(500);
ALTER TABLE request_audit_log ADD COLUMN IF NOT EXISTS file_type VARCHAR(10);
ALTER TABLE request_audit_log ADD COLUMN IF NOT EXISTS detected_mime_type VARCHAR(100);
ALTER TABLE request_audit_log ADD COLUMN IF NOT EXISTS file_size_bytes BIGINT;
ALTER TABLE request_audit_log ADD COLUMN IF NOT EXISTS file_hash VARCHAR(64);
ALTER TABLE request_audit_log ADD COLUMN IF NOT EXISTS user_agent VARCHAR(500);
ALTER TABLE request_audit_log ADD COLUMN IF NOT EXISTS request_headers TEXT;

-- Widen result_message to hold longer threat descriptions
ALTER TABLE request_audit_log ALTER COLUMN result_message TYPE VARCHAR(2000);

CREATE INDEX IF NOT EXISTS idx_audit_log_event_type ON request_audit_log(event_type);
CREATE INDEX IF NOT EXISTS idx_audit_log_severity ON request_audit_log(severity);
CREATE INDEX IF NOT EXISTS idx_audit_log_timestamp ON request_audit_log(request_timestamp);

-- 2) File attachments linked to pending_requests
CREATE TABLE IF NOT EXISTS file_attachments (
    id BIGSERIAL PRIMARY KEY,
    file_id UUID NOT NULL UNIQUE,
    request_id BIGINT REFERENCES pending_requests(id) ON DELETE CASCADE,
    original_filename VARCHAR(500) NOT NULL,
    stored_filename VARCHAR(500) NOT NULL,
    file_type VARCHAR(10) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    file_hash VARCHAR(64) NOT NULL,
    storage_path VARCHAR(1000) NOT NULL,
    scan_status VARCHAR(15) NOT NULL DEFAULT 'PENDING',
    scan_details VARCHAR(2000),
    uploaded_at TIMESTAMP,
    scanned_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_file_attachment_request_id ON file_attachments(request_id);
CREATE INDEX IF NOT EXISTS idx_file_attachment_file_type ON file_attachments(file_type);
CREATE INDEX IF NOT EXISTS idx_file_attachment_scan_status ON file_attachments(scan_status);

-- 3) File automation rules
CREATE TABLE IF NOT EXISTS file_automation_rules (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    enabled BOOLEAN NOT NULL DEFAULT true,
    priority INTEGER NOT NULL DEFAULT 100,
    file_types VARCHAR(100),
    max_file_size_bytes BIGINT,
    min_file_size_bytes BIGINT,
    filename_pattern VARCHAR(200),
    query_types VARCHAR(50),
    requestor_groups VARCHAR(500),
    action VARCHAR(20) NOT NULL DEFAULT 'FLAG_FOR_REVIEW',
    action_message VARCHAR(1000),
    action_config JSONB,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    trigger_count BIGINT NOT NULL DEFAULT 0,
    last_triggered_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_file_auto_rule_enabled ON file_automation_rules(enabled);
CREATE INDEX IF NOT EXISTS idx_file_auto_rule_priority ON file_automation_rules(priority);
