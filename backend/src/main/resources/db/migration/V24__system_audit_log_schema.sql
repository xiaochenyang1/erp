CREATE TABLE IF NOT EXISTS sys_audit_log (
    id BIGINT PRIMARY KEY,
    audit_type VARCHAR(64) NOT NULL,
    business_type VARCHAR(64) NOT NULL,
    business_id BIGINT,
    business_no VARCHAR(128),
    action VARCHAR(128) NOT NULL,
    operator_id BIGINT,
    operator_name VARCHAR(64),
    snapshot_json TEXT,
    message VARCHAR(512),
    audit_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sys_audit_log_business ON sys_audit_log (business_type, business_id);
CREATE INDEX idx_sys_audit_log_business_no ON sys_audit_log (business_no);
CREATE INDEX idx_sys_audit_log_audit_time ON sys_audit_log (audit_time);
