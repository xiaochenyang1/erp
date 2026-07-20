CREATE TABLE IF NOT EXISTS wf_approval_config (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    business_type VARCHAR(64) NOT NULL,
    config_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

ALTER TABLE wf_approval_task
    ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE wf_approval_task
    ADD COLUMN account_book_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE wf_approval_record
    ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE wf_approval_record
    ADD COLUMN account_book_id BIGINT NOT NULL DEFAULT 1;

CREATE TABLE IF NOT EXISTS wf_approval_node (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    config_id BIGINT NOT NULL,
    node_name VARCHAR(128) NOT NULL,
    node_order INT NOT NULL DEFAULT 1,
    approval_mode VARCHAR(32) NOT NULL DEFAULT 'ANY' CHECK (approval_mode IN ('ANY', 'ALL')),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS wf_approval_node_approver (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    approver_type VARCHAR(32) NOT NULL CHECK (approver_type IN ('USER', 'ROLE')),
    approver_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_wf_approval_config_business
    ON wf_approval_config (company_id, account_book_id, business_type, deleted_flag);
CREATE INDEX idx_wf_approval_config_status
    ON wf_approval_config (company_id, account_book_id, status);
CREATE INDEX idx_wf_approval_node_config
    ON wf_approval_node (company_id, config_id, status, node_order);
CREATE INDEX idx_wf_approval_node_approver
    ON wf_approval_node_approver (company_id, node_id, approver_type, approver_id);
CREATE INDEX idx_wf_task_company_book_source_status
    ON wf_approval_task (company_id, account_book_id, business_type, business_id, status);
CREATE INDEX idx_wf_record_company_book_source
    ON wf_approval_record (company_id, account_book_id, business_type, business_id);

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag, status, deleted_flag, created_by, updated_by, version)
VALUES
    (5064, 5010, 'MENU', 'WORKFLOW_CONFIG', '审批配置', '/workflow/configs', 'workflow/config/index', 'workflow:config:view', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5065, 5064, 'BUTTON', 'WORKFLOW_CONFIG_UPDATE', '维护审批配置', NULL, NULL, 'workflow:config:update', 1, 1, 'ACTIVE', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_type = VALUES(menu_type),
    menu_name = VALUES(menu_name),
    path = VALUES(path),
    component = VALUES(component),
    permission = VALUES(permission),
    sort_no = VALUES(sort_no),
    visible_flag = VALUES(visible_flag),
    status = VALUES(status),
    deleted_flag = VALUES(deleted_flag);

INSERT INTO sys_role_menu
(id, role_id, menu_id, created_by)
VALUES
    (7124, 3002, 5064, 0),
    (7125, 3002, 5065, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
