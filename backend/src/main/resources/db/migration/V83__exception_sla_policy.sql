ALTER TABLE biz_exception_ticket_event DROP CONSTRAINT chk_biz_exception_ticket_event_action;
ALTER TABLE biz_exception_ticket_event
    ADD CONSTRAINT chk_biz_exception_ticket_event_action
    CHECK (action IN ('CREATE', 'ASSIGN', 'START', 'RESOLVE', 'CLOSE', 'ESCALATE'));

CREATE TABLE IF NOT EXISTS biz_exception_sla_policy (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    category VARCHAR(64) NOT NULL,
    priority VARCHAR(16) NOT NULL,
    due_hours INT NOT NULL,
    escalation_enabled TINYINT NOT NULL DEFAULT 1,
    escalate_to_priority VARCHAR(16) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    remark VARCHAR(512),
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_biz_exception_sla_policy_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT chk_biz_exception_sla_policy_due_hours CHECK (due_hours BETWEEN 1 AND 8760),
    CONSTRAINT chk_biz_exception_sla_policy_escalate_to CHECK (escalate_to_priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT chk_biz_exception_sla_policy_escalation_enabled CHECK (escalation_enabled IN (0, 1)),
    CONSTRAINT chk_biz_exception_sla_policy_enabled CHECK (enabled IN (0, 1))
);

CREATE UNIQUE INDEX uk_biz_exception_sla_policy_category_priority
    ON biz_exception_sla_policy (company_id, account_book_id, category, priority);
CREATE INDEX idx_biz_exception_sla_policy_query
    ON biz_exception_sla_policy (company_id, account_book_id, enabled, deleted_flag, category, priority);

INSERT INTO biz_exception_sla_policy
(id, company_id, account_book_id, category, priority, due_hours, escalation_enabled,
 escalate_to_priority, enabled, remark, created_by, updated_by, version)
VALUES
    (8301, 1, 1, 'GENERAL', 'LOW', 168, 1, 'MEDIUM', 1, '通用低优先级 SLA', 0, 0, 0),
    (8302, 1, 1, 'GENERAL', 'MEDIUM', 72, 1, 'HIGH', 1, '通用中优先级 SLA', 0, 0, 0),
    (8303, 1, 1, 'GENERAL', 'HIGH', 24, 1, 'URGENT', 1, '通用高优先级 SLA', 0, 0, 0),
    (8304, 1, 1, 'GENERAL', 'URGENT', 4, 1, 'URGENT', 1, '通用紧急 SLA', 0, 0, 0),
    (8305, 1, 1, 'LOW_STOCK', 'HIGH', 24, 1, 'URGENT', 1, '低库存高优先级 SLA', 0, 0, 0),
    (8306, 1, 1, 'PAYMENT_OVERDUE', 'MEDIUM', 72, 1, 'HIGH', 1, '逾期收付中优先级 SLA', 0, 0, 0),
    (8307, 1, 1, 'PAYMENT_OVERDUE', 'HIGH', 24, 1, 'URGENT', 1, '逾期收付高优先级 SLA', 0, 0, 0),
    (8308, 1, 1, 'SYSTEM_ERROR', 'MEDIUM', 72, 1, 'HIGH', 1, '系统失败中优先级 SLA', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    due_hours = VALUES(due_hours),
    escalation_enabled = VALUES(escalation_enabled),
    escalate_to_priority = VALUES(escalate_to_priority),
    enabled = VALUES(enabled),
    remark = VALUES(remark),
    updated_by = VALUES(updated_by);

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5101, 0, 'MENU', 'EXCEPTION_SLA_POLICY', '异常SLA策略', '/exception-sla-policies', 'exception-sla-policies/index',
     'exception-sla-policy:view', 14, 1, 'ACTIVE', 0, 0, 0, 0),
    (5102, 5101, 'BUTTON', 'EXCEPTION_SLA_POLICY_MANAGE', '维护异常SLA策略', NULL, NULL,
     'exception-sla-policy:manage', 1, 1, 'ACTIVE', 0, 0, 0, 0)
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
    (7145, 3002, 5101, 0),
    (7146, 3002, 5102, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
