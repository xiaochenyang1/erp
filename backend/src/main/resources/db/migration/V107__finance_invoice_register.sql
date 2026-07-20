-- V107: 进项/销项发票登记（最小登记能力，不做税务申报）。
-- 状态机：DRAFT → POSTED；DRAFT/POSTED → CANCELLED。
-- 号段：sequence id=2031（已占用至 2030=QC_INSPECTION）；
--       menu id 5340-5341（已占用至 5331=V106）；
--       role_menu id 7350-7351（已占用至 7341=V106）。

CREATE TABLE IF NOT EXISTS fin_invoice_register (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    invoice_no VARCHAR(64) NOT NULL,
    invoice_type VARCHAR(16) NOT NULL,
    partner_name VARCHAR(128) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    invoice_date DATE NOT NULL,
    related_biz_type VARCHAR(64),
    related_biz_id BIGINT,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(512),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_fin_invoice_register_type
        CHECK (invoice_type IN ('INPUT', 'OUTPUT')),
    CONSTRAINT chk_fin_invoice_register_status
        CHECK (status IN ('DRAFT', 'POSTED', 'CANCELLED'))
);

CREATE UNIQUE INDEX uk_fin_invoice_register_company_book_no
    ON fin_invoice_register (company_id, account_book_id, invoice_no);
CREATE INDEX idx_fin_invoice_register_status_date
    ON fin_invoice_register (company_id, account_book_id, status, invoice_date);
CREATE INDEX idx_fin_invoice_register_type_date
    ON fin_invoice_register (company_id, account_book_id, invoice_type, invoice_date);
CREATE INDEX idx_fin_invoice_register_related
    ON fin_invoice_register (company_id, account_book_id, related_biz_type, related_biz_id);

-- 发票登记号规则：FI + yyyyMMdd + 4 位序号（company/account_book 作用域）
INSERT INTO sys_sequence_rule
(id, company_id, account_book_id, biz_type, prefix, date_pattern, seq_length, current_value,
 status, created_by, updated_by, version)
VALUES
    (2031, 1, 1, 'FIN_INVOICE', 'FI', 'yyyyMMdd', 4, 0, 'ACTIVE', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    prefix = VALUES(prefix),
    date_pattern = VALUES(date_pattern),
    seq_length = VALUES(seq_length),
    current_value = GREATEST(current_value, VALUES(current_value)),
    status = VALUES(status);

-- 菜单挂财务管理 CATALOG 5030；ERP_ADMIN(3002) 绑定
INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5340, 5030, 'MENU', 'FINANCE_INVOICE', '发票登记', '/finance/invoices',
     'finance/invoices/index', 'finance:invoice:view', 10, 1, 'ACTIVE', 0, 0, 0, 0),
    (5341, 5340, 'BUTTON', 'FINANCE_INVOICE_MANAGE', '维护发票登记', NULL, NULL,
     'finance:invoice:manage', 1, 1, 'ACTIVE', 0, 0, 0, 0)
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
    (7350, 3002, 5340, 0),
    (7351, 3002, 5341, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
