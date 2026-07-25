-- V133: 采购请购单（PR）轻量闭环。
-- 状态：DRAFT -> SUBMITTED -> APPROVED -> CONVERTED / CANCELLED / REJECTED
-- 号段：menu 5470-5471；role_menu 7480-7481；sequence 2035。

CREATE TABLE IF NOT EXISTS pur_requisition (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    requisition_no VARCHAR(64) NOT NULL,
    requisition_date DATE NOT NULL,
    request_dept_id BIGINT NULL,
    request_user_id BIGINT NULL,
    needed_date DATE NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    supplier_id BIGINT NULL,
    converted_order_id BIGINT NULL,
    converted_order_no VARCHAR(64) NULL,
    converted_time TIMESTAMP NULL,
    remark VARCHAR(255),
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_pur_requisition_status CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','CONVERTED','CANCELLED'))
);

CREATE UNIQUE INDEX uk_pur_requisition_company_book_no
    ON pur_requisition (company_id, account_book_id, requisition_no);
CREATE INDEX idx_pur_requisition_company_book_status
    ON pur_requisition (company_id, account_book_id, status, deleted_flag, requisition_date);

CREATE TABLE IF NOT EXISTS pur_requisition_line (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    requisition_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    product_id BIGINT NOT NULL,
    qty DECIMAL(18,4) NOT NULL,
    remark VARCHAR(255),
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_pur_requisition_line_req
    ON pur_requisition_line (company_id, account_book_id, requisition_id, deleted_flag, line_no);

INSERT INTO sys_sequence_rule
(id, company_id, account_book_id, biz_type, prefix, date_pattern, seq_length, current_value,
 status, created_by, updated_by, version)
VALUES
    (2035, 1, 1, 'PURCHASE_REQUISITION', 'PRQ', 'yyyyMMdd', 4, 0, 'ACTIVE', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    company_id = VALUES(company_id),
    account_book_id = VALUES(account_book_id),
    prefix = VALUES(prefix),
    current_value = GREATEST(COALESCE(current_value, 0), VALUES(current_value)),
    status = VALUES(status);

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5470, 5135, 'MENU', 'PURCHASE_REQUISITION', '采购请购', '/purchase/requisitions',
     'purchase/requisitions/index', 'purchase:requisition:view', 6, 1, 'ACTIVE', 0, 0, 0, 0),
    (5471, 5470, 'BUTTON', 'PURCHASE_REQUISITION_MANAGE', '维护请购单', NULL, NULL,
     'purchase:requisition:manage', 1, 1, 'ACTIVE', 0, 0, 0, 0)
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
    (7480, 3002, 5470, 0),
    (7481, 3002, 5471, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
