CREATE TABLE IF NOT EXISTS biz_exception_ticket (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    ticket_no VARCHAR(64) NOT NULL,
    category VARCHAR(64) NOT NULL,
    priority VARCHAR(16) NOT NULL,
    title VARCHAR(128) NOT NULL,
    description VARCHAR(1024),
    source_type VARCHAR(64),
    source_id BIGINT,
    source_no VARCHAR(128),
    source_route VARCHAR(512),
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    assignee_user_id BIGINT,
    due_time TIMESTAMP,
    resolved_by BIGINT,
    resolved_time TIMESTAMP,
    resolution VARCHAR(512),
    closed_by BIGINT,
    closed_time TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_biz_exception_ticket_status CHECK (status IN ('OPEN', 'PROCESSING', 'RESOLVED', 'CLOSED')),
    CONSTRAINT chk_biz_exception_ticket_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT'))
);

CREATE TABLE IF NOT EXISTS biz_exception_ticket_event (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    ticket_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32),
    comment VARCHAR(512),
    operator_user_id BIGINT NOT NULL,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_biz_exception_ticket_event_action CHECK (action IN ('CREATE', 'ASSIGN', 'START', 'RESOLVE', 'CLOSE'))
);

CREATE UNIQUE INDEX uk_biz_exception_ticket_company_book_no
    ON biz_exception_ticket (company_id, account_book_id, ticket_no);
CREATE INDEX idx_biz_exception_ticket_status_due
    ON biz_exception_ticket (company_id, account_book_id, status, due_time);
CREATE INDEX idx_biz_exception_ticket_priority_time
    ON biz_exception_ticket (company_id, account_book_id, priority, updated_time);
CREATE INDEX idx_biz_exception_ticket_assignee_status
    ON biz_exception_ticket (company_id, account_book_id, assignee_user_id, status, due_time);
CREATE INDEX idx_biz_exception_ticket_source
    ON biz_exception_ticket (company_id, account_book_id, source_type, source_id, source_no);
CREATE INDEX idx_biz_exception_ticket_event_ticket_time
    ON biz_exception_ticket_event (company_id, account_book_id, ticket_id, created_time);

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5096, 0, 'MENU', 'EXCEPTION_TICKET', '异常处理', '/exception-tickets', 'exception-tickets/index',
     'exception-ticket:view', 12, 1, 'ACTIVE', 0, 0, 0, 0),
    (5097, 5096, 'BUTTON', 'EXCEPTION_TICKET_MANAGE', '维护异常工单', NULL, NULL,
     'exception-ticket:manage', 1, 1, 'ACTIVE', 0, 0, 0, 0)
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
    (7140, 3002, 5096, 0),
    (7141, 3002, 5097, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
