CREATE TABLE IF NOT EXISTS fin_account_subject (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    subject_code VARCHAR(64) NOT NULL,
    subject_name VARCHAR(128) NOT NULL,
    parent_id BIGINT,
    subject_type VARCHAR(32) NOT NULL,
    balance_direction VARCHAR(16) NOT NULL CHECK (balance_direction IN ('DEBIT', 'CREDIT')),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS fin_expense (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    expense_no VARCHAR(64) NOT NULL,
    expense_date DATE NOT NULL,
    subject_id BIGINT NOT NULL,
    payment_subject_id BIGINT NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'POSTED', 'CANCELLED')),
    voucher_id BIGINT,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS fin_voucher_entry (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    voucher_id BIGINT NOT NULL,
    biz_date DATE NOT NULL,
    line_no INT NOT NULL,
    subject_id BIGINT NOT NULL,
    subject_code VARCHAR(64) NOT NULL,
    subject_name VARCHAR(128) NOT NULL,
    debit_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    credit_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    summary VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_fin_account_subject_company_code
    ON fin_account_subject (company_id, subject_code);
CREATE INDEX idx_fin_account_subject_company_parent
    ON fin_account_subject (company_id, parent_id);
CREATE UNIQUE INDEX uk_fin_expense_company_no
    ON fin_expense (company_id, expense_no);
CREATE INDEX idx_fin_expense_company_date
    ON fin_expense (company_id, expense_date);
CREATE UNIQUE INDEX uk_fin_voucher_entry_voucher_line
    ON fin_voucher_entry (voucher_id, line_no);
CREATE INDEX idx_fin_voucher_entry_company_subject
    ON fin_voucher_entry (company_id, subject_code, biz_date);
CREATE INDEX idx_fin_voucher_entry_company_voucher
    ON fin_voucher_entry (company_id, voucher_id);

INSERT INTO fin_account_subject
(id, company_id, account_book_id, subject_code, subject_name, parent_id, subject_type, balance_direction,
 status, deleted_flag, remark, created_by, updated_by, version)
VALUES
    (910001, 1, 1, '1001', '库存商品', NULL, 'ASSET', 'DEBIT', 'ACTIVE', 0, '业务自动凭证库存借方科目', 0, 0, 0),
    (910002, 1, 1, '1002', '银行存款', NULL, 'ASSET', 'DEBIT', 'ACTIVE', 0, '费用支付默认贷方科目', 0, 0, 0),
    (910003, 1, 1, '1122', '应收账款', NULL, 'ASSET', 'DEBIT', 'ACTIVE', 0, '销售业务应收科目', 0, 0, 0),
    (910004, 1, 1, '2202', '应付账款', NULL, 'LIABILITY', 'CREDIT', 'ACTIVE', 0, '采购业务应付科目', 0, 0, 0),
    (910005, 1, 1, '6001', '主营业务收入', NULL, 'REVENUE', 'CREDIT', 'ACTIVE', 0, '销售出库收入科目', 0, 0, 0),
    (910006, 1, 1, '6401', '销售退回', NULL, 'REVENUE', 'DEBIT', 'ACTIVE', 0, '销售退货冲减收入科目', 0, 0, 0),
    (910007, 1, 1, '6602', '管理费用', NULL, 'EXPENSE', 'DEBIT', 'ACTIVE', 0, '费用登记默认费用科目', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    subject_name = VALUES(subject_name),
    parent_id = VALUES(parent_id),
    subject_type = VALUES(subject_type),
    balance_direction = VALUES(balance_direction),
    status = VALUES(status),
    deleted_flag = VALUES(deleted_flag),
    remark = VALUES(remark),
    updated_by = VALUES(updated_by);

INSERT INTO sys_sequence_rule
(id, biz_type, prefix, date_pattern, seq_length, current_value, status, created_by, updated_by, version)
VALUES
    (2012, 'FIN_EXPENSE', 'FE', 'yyyyMMdd', 4, 0, 'ACTIVE', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    prefix = VALUES(prefix),
    date_pattern = VALUES(date_pattern),
    seq_length = VALUES(seq_length),
    current_value = GREATEST(current_value, VALUES(current_value)),
    status = VALUES(status);

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5030, 0, 'CATALOG', 'FINANCE', '财务管理', '/finance', 'Layout', NULL, 8, 1, 'ACTIVE', 0, 0, 0, 0),
    (5031, 5030, 'MENU', 'FINANCE_SUBJECT', '会计科目', '/finance/account-subjects',
     'finance/account-subject/index', 'finance:subject:manage', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5032, 5030, 'MENU', 'FINANCE_EXPENSE', '费用登记', '/finance/expenses',
     'finance/expense/index', 'finance:expense:manage', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5033, 5030, 'MENU', 'FINANCE_VOUCHER', '记账凭证', '/finance/vouchers',
     'finance/voucher/index', 'finance:voucher:view', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5034, 5030, 'MENU', 'FINANCE_LEDGER', '账簿查询', '/finance/ledger',
     'finance/ledger/index', 'finance:ledger:view', 4, 1, 'ACTIVE', 0, 0, 0, 0)
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
    (7050, 3002, 5030, 0),
    (7051, 3002, 5031, 0),
    (7052, 3002, 5032, 0),
    (7053, 3002, 5033, 0),
    (7054, 3002, 5034, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
