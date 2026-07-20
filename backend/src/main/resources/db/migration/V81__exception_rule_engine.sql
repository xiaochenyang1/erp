CREATE TABLE IF NOT EXISTS biz_exception_rule (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    rule_name VARCHAR(128) NOT NULL,
    rule_type VARCHAR(64) NOT NULL,
    category VARCHAR(64) NOT NULL,
    priority VARCHAR(16) NOT NULL,
    threshold_value DECIMAL(18, 4) NOT NULL DEFAULT 0,
    threshold_unit VARCHAR(16) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    assignee_user_id BIGINT,
    remark VARCHAR(512),
    last_scan_time TIMESTAMP NULL,
    last_scan_status VARCHAR(32),
    last_hit_count INT NOT NULL DEFAULT 0,
    last_ticket_created_count INT NOT NULL DEFAULT 0,
    last_error_message VARCHAR(512),
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_biz_exception_rule_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT chk_biz_exception_rule_threshold_unit CHECK (threshold_unit IN ('QTY', 'DAYS', 'MINUTES', 'COUNT')),
    CONSTRAINT chk_biz_exception_rule_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_biz_exception_rule_scan_status CHECK (last_scan_status IS NULL OR last_scan_status IN ('SUCCESS', 'FAILED', 'SKIPPED'))
);

CREATE TABLE IF NOT EXISTS biz_exception_rule_hit (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    rule_id BIGINT NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    rule_type VARCHAR(64) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_id BIGINT,
    source_no VARCHAR(128),
    source_route VARCHAR(512),
    hit_key VARCHAR(256) NOT NULL,
    title VARCHAR(128) NOT NULL,
    description VARCHAR(1024),
    trigger_value VARCHAR(64),
    threshold_value VARCHAR(64),
    ticket_id BIGINT,
    hit_count INT NOT NULL DEFAULT 1,
    first_hit_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_hit_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_biz_exception_rule_company_book_code
    ON biz_exception_rule (company_id, account_book_id, rule_code);
CREATE INDEX idx_biz_exception_rule_type_enabled
    ON biz_exception_rule (company_id, account_book_id, rule_type, enabled, deleted_flag);
CREATE INDEX idx_biz_exception_rule_scan
    ON biz_exception_rule (company_id, account_book_id, last_scan_time, last_scan_status);

CREATE UNIQUE INDEX uk_biz_exception_rule_hit_key
    ON biz_exception_rule_hit (company_id, account_book_id, rule_id, hit_key);
CREATE INDEX idx_biz_exception_rule_hit_rule_time
    ON biz_exception_rule_hit (company_id, account_book_id, rule_id, last_hit_time);
CREATE INDEX idx_biz_exception_rule_hit_source
    ON biz_exception_rule_hit (company_id, account_book_id, source_type, source_id, source_no);
CREATE INDEX idx_biz_exception_rule_hit_ticket
    ON biz_exception_rule_hit (company_id, account_book_id, ticket_id);

INSERT INTO biz_exception_rule
(id, company_id, account_book_id, rule_code, rule_name, rule_type, category, priority,
 threshold_value, threshold_unit, enabled, remark, created_by, updated_by, version)
VALUES
    (8101, 1, 1, 'LOW_STOCK_DEFAULT', '低库存自动工单', 'LOW_STOCK', 'LOW_STOCK', 'HIGH',
     0, 'QTY', 1, '扫描库存预警规则命中的低库存项目', 0, 0, 0),
    (8102, 1, 1, 'RECEIVABLE_OVERDUE_DEFAULT', '应收逾期自动工单', 'RECEIVABLE_OVERDUE', 'PAYMENT_OVERDUE', 'HIGH',
     30, 'DAYS', 1, '扫描超过阈值天数仍未结清的应收单', 0, 0, 0),
    (8103, 1, 1, 'PAYABLE_OVERDUE_DEFAULT', '应付逾期自动工单', 'PAYABLE_OVERDUE', 'PAYMENT_OVERDUE', 'MEDIUM',
     30, 'DAYS', 1, '扫描超过阈值天数仍未结清的应付单', 0, 0, 0),
    (8104, 1, 1, 'OPERATION_FAILURE_DEFAULT', '失败操作日志自动工单', 'OPERATION_FAILURE', 'SYSTEM_ERROR', 'MEDIUM',
     1440, 'MINUTES', 1, '扫描最近窗口内的失败操作日志', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    rule_name = VALUES(rule_name),
    rule_type = VALUES(rule_type),
    category = VALUES(category),
    priority = VALUES(priority),
    threshold_value = VALUES(threshold_value),
    threshold_unit = VALUES(threshold_unit),
    enabled = VALUES(enabled),
    remark = VALUES(remark),
    updated_by = VALUES(updated_by);

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5098, 0, 'MENU', 'EXCEPTION_RULE', '异常规则', '/exception-rules', 'exception-rules/index',
     'exception-rule:view', 13, 1, 'ACTIVE', 0, 0, 0, 0),
    (5099, 5098, 'BUTTON', 'EXCEPTION_RULE_MANAGE', '维护异常规则', NULL, NULL,
     'exception-rule:manage', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5100, 5098, 'BUTTON', 'EXCEPTION_RULE_EXECUTE', '执行异常扫描', NULL, NULL,
     'exception-rule:execute', 2, 1, 'ACTIVE', 0, 0, 0, 0)
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
    deleted_flag = VALUES(deleted_flag),
    updated_by = VALUES(updated_by);

INSERT INTO sys_role_menu
(id, role_id, menu_id, created_by)
VALUES
    (7142, 3002, 5098, 0),
    (7143, 3002, 5099, 0),
    (7144, 3002, 5100, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
