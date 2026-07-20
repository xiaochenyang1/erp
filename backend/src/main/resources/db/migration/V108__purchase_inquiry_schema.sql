-- V108: 采购询价/RFQ 最小闭环。
-- 表结构对齐 pur_order / qc_inspection 风格:多租户 company_id/account_book_id、deleted_flag、version 与 4 审计列。
-- 流程: DRAFT 创建/编辑 -> SUBMITTED 录入报价 -> 选定中标报价 CLOSED(selected_supplier_id) -> po-prefill 供前端调已有 PO 创建 API。
-- 号段: sequence_rule id=2032; menu id 5350 段; role_menu id 7360 段。

CREATE TABLE IF NOT EXISTS pur_inquiry (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    inquiry_no VARCHAR(64) NOT NULL,
    inquiry_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    selected_supplier_id BIGINT,
    selected_quote_id BIGINT,
    title VARCHAR(128),
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS pur_inquiry_line (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    inquiry_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    product_id BIGINT NOT NULL,
    qty DECIMAL(18, 4) NOT NULL,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS pur_inquiry_quote (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    inquiry_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    unit_price DECIMAL(18, 2),
    tax_rate DECIMAL(8, 4),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_pur_inquiry_company_book_no
    ON pur_inquiry (company_id, account_book_id, inquiry_no);
CREATE INDEX idx_pur_inquiry_company_book_status
    ON pur_inquiry (company_id, account_book_id, status);
CREATE INDEX idx_pur_inquiry_company_book_date
    ON pur_inquiry (company_id, account_book_id, inquiry_date);

CREATE UNIQUE INDEX uk_pur_inquiry_line_company_book_line
    ON pur_inquiry_line (company_id, account_book_id, inquiry_id, line_no);
CREATE INDEX idx_pur_inquiry_line_company_book_inquiry
    ON pur_inquiry_line (company_id, account_book_id, inquiry_id);

CREATE UNIQUE INDEX uk_pur_inquiry_quote_company_book_supplier
    ON pur_inquiry_quote (company_id, account_book_id, inquiry_id, supplier_id);
CREATE INDEX idx_pur_inquiry_quote_company_book_inquiry
    ON pur_inquiry_quote (company_id, account_book_id, inquiry_id);

-- 询价单号规则(id 用 2032 段, 避免与 QC 2030 / 手工凭证 2014 等冲突)
INSERT INTO sys_sequence_rule
(id, biz_type, prefix, date_pattern, seq_length, current_value, status, created_by, updated_by, version)
VALUES
    (2032, 'PURCHASE_INQUIRY', 'RFQ', 'yyyyMMdd', 4, 1, 'ACTIVE', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    prefix = VALUES(prefix),
    date_pattern = VALUES(date_pattern),
    seq_length = VALUES(seq_length),
    current_value = GREATEST(current_value, VALUES(current_value)),
    status = VALUES(status);

-- 菜单挂在采购 CATALOG(5135) 下, sort 5; menu 5350 段, role_menu 7360 段。
-- permission 与 PurchasePermissionCodes / @PreAuthorize / 前端 v-permission 三方对齐。
-- SUPER_ADMIN(3001) 走全树;此处绑 ERP_ADMIN(3002)。
INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5350, 5135, 'MENU', 'PURCHASE_INQUIRY', '采购询价', '/purchase/inquiries',
     'purchase/inquiries/index', 'purchase:inquiry:view', 5, 1, 'ACTIVE', 0, 0, 0, 0),
    (5351, 5350, 'BUTTON', 'PURCHASE_INQUIRY_MANAGE', '管理采购询价', NULL, NULL,
     'purchase:inquiry:manage', 1, 1, 'ACTIVE', 0, 0, 0, 0)
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
    (7360, 3002, 5350, 0),
    (7361, 3002, 5351, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
