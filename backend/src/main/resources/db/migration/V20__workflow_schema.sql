CREATE TABLE IF NOT EXISTS wf_approval_instance (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    business_type VARCHAR(64) NOT NULL,
    business_id BIGINT NOT NULL,
    business_no VARCHAR(64) NOT NULL,
    title VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('IN_APPROVAL', 'APPROVED', 'REJECTED', 'CANCELLED')),
    submit_user_id BIGINT NOT NULL,
    submit_time TIMESTAMP NOT NULL,
    completed_time TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS wf_approval_task (
    id BIGINT PRIMARY KEY,
    instance_id BIGINT NOT NULL,
    business_type VARCHAR(64) NOT NULL,
    business_id BIGINT NOT NULL,
    business_no VARCHAR(64) NOT NULL,
    title VARCHAR(128) NOT NULL,
    approver_user_id BIGINT,
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS wf_approval_record (
    id BIGINT PRIMARY KEY,
    instance_id BIGINT NOT NULL,
    business_type VARCHAR(64) NOT NULL,
    business_id BIGINT NOT NULL,
    business_no VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL CHECK (action IN ('SUBMIT', 'APPROVE', 'REJECT', 'CANCEL')),
    operator_user_id BIGINT NOT NULL,
    comment VARCHAR(255),
    action_time TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_wf_instance_active_source
    ON wf_approval_instance (business_type, business_id, status);
CREATE INDEX idx_wf_instance_source
    ON wf_approval_instance (business_type, business_id);
CREATE INDEX idx_wf_task_instance_status
    ON wf_approval_task (instance_id, status);
CREATE INDEX idx_wf_task_source_status
    ON wf_approval_task (business_type, business_id, status);
CREATE INDEX idx_wf_record_source
    ON wf_approval_record (business_type, business_id);
