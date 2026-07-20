-- V116: 销售报价单最小闭环
-- 流程: DRAFT 编辑 -> CONFIRMED -> 转销售订单草稿 / CANCELLED
-- menu 5400-5401; role_menu 7410-7411; sequence 2033

CREATE TABLE IF NOT EXISTS sal_quote (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    quote_no VARCHAR(64) NOT NULL,
    customer_id BIGINT NOT NULL,
    quote_date DATE NOT NULL,
    valid_until DATE NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    total_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    total_tax_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    converted_order_id BIGINT NULL,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_sal_quote_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'CONVERTED', 'CANCELLED'))
);

CREATE TABLE IF NOT EXISTS sal_quote_line (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    quote_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    product_id BIGINT NOT NULL,
    qty DECIMAL(18, 4) NOT NULL,
    price DECIMAL(18, 2) NOT NULL,
    tax_rate DECIMAL(8, 4) NOT NULL DEFAULT 0,
    amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_sal_quote_company_book_no ON sal_quote (company_id, account_book_id, quote_no);
CREATE INDEX idx_sal_quote_customer_status ON sal_quote (company_id, account_book_id, customer_id, status);
CREATE UNIQUE INDEX uk_sal_quote_line_no ON sal_quote_line (company_id, account_book_id, quote_id, line_no);

INSERT INTO sys_sequence_rule
(id, company_id, account_book_id, biz_type, prefix, date_pattern, seq_length, current_value,
 status, created_by, updated_by, version)
VALUES
    (2033, 1, 1, 'SALES_QUOTE', 'SQ', 'yyyyMMdd', 4, 0, 'ACTIVE', 0, 0, 0)
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
    (5400, 5139, 'MENU', 'SALES_QUOTE', '销售报价', '/sales/quotes',
     'sales/quotes/index', 'sales:quote:view', 5, 1, 'ACTIVE', 0, 0, 0, 0),
    (5401, 5400, 'BUTTON', 'SALES_QUOTE_MANAGE', '维护销售报价', NULL, NULL,
     'sales:quote:manage', 1, 1, 'ACTIVE', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_name = VALUES(menu_name),
    path = VALUES(path),
    component = VALUES(component),
    permission = VALUES(permission),
    sort_no = VALUES(sort_no),
    status = VALUES(status),
    deleted_flag = VALUES(deleted_flag);

INSERT INTO sys_role_menu (id, role_id, menu_id, created_by) VALUES
    (7410, 3002, 5400, 0),
    (7411, 3002, 5401, 0)
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id), menu_id = VALUES(menu_id);
