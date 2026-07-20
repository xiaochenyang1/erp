CREATE TABLE IF NOT EXISTS fin_fund_account (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    account_code VARCHAR(64) NOT NULL,
    account_name VARCHAR(128) NOT NULL,
    account_type VARCHAR(32) NOT NULL,
    bank_name VARCHAR(128),
    bank_account_no VARCHAR(128),
    currency_code VARCHAR(16) NOT NULL DEFAULT 'CNY',
    opening_balance DECIMAL(18, 2) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(512),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_fin_fund_account_type CHECK (account_type IN ('BANK', 'CASH')),
    CONSTRAINT chk_fin_fund_account_status CHECK (status IN ('ENABLED', 'DISABLED'))
);

CREATE TABLE IF NOT EXISTS fin_bank_statement (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    fund_account_id BIGINT NOT NULL,
    statement_no VARCHAR(64) NOT NULL,
    external_txn_no VARCHAR(128),
    transaction_date DATE NOT NULL,
    direction VARCHAR(16) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    counterparty_name VARCHAR(128),
    summary VARCHAR(256) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'UNMATCHED',
    matched_biz_type VARCHAR(32),
    matched_biz_id BIGINT,
    matched_biz_no VARCHAR(64),
    matched_time TIMESTAMP,
    matched_by BIGINT,
    unmatch_reason VARCHAR(512),
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(512),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_fin_bank_statement_direction CHECK (direction IN ('IN', 'OUT')),
    CONSTRAINT chk_fin_bank_statement_status CHECK (status IN ('UNMATCHED', 'MATCHED', 'CANCELLED')),
    CONSTRAINT chk_fin_bank_statement_biz_type CHECK (matched_biz_type IS NULL OR matched_biz_type IN ('RECEIPT', 'PAYMENT'))
);

CREATE UNIQUE INDEX uk_fin_fund_account_code
    ON fin_fund_account (company_id, account_book_id, account_code);
CREATE INDEX idx_fin_fund_account_status_type
    ON fin_fund_account (company_id, account_book_id, status, account_type);

CREATE UNIQUE INDEX uk_fin_bank_statement_no
    ON fin_bank_statement (company_id, account_book_id, statement_no);
CREATE INDEX idx_fin_bank_statement_account_date
    ON fin_bank_statement (company_id, account_book_id, fund_account_id, transaction_date);
CREATE INDEX idx_fin_bank_statement_status_date
    ON fin_bank_statement (company_id, account_book_id, status, transaction_date);
CREATE INDEX idx_fin_bank_statement_biz
    ON fin_bank_statement (company_id, account_book_id, matched_biz_type, matched_biz_id);
CREATE INDEX idx_fin_bank_statement_external
    ON fin_bank_statement (company_id, account_book_id, external_txn_no);

INSERT INTO sys_sequence_rule
(id, company_id, biz_type, prefix, date_pattern, seq_length, current_value, status, created_by, updated_by, version)
VALUES
    (2013, 1, 'FIN_BANK_STATEMENT', 'BS', 'yyyyMMdd', 4, 0, 'ACTIVE', 0, 0, 0)
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
    (5041, 5030, 'MENU', 'FINANCE_FUND', '资金对账', '/finance/funds',
     'finance/fund/index', 'finance:fund:view', 8, 1, 'ACTIVE', 0, 0, 0, 0),
    (5042, 5041, 'BUTTON', 'FINANCE_FUND_MANAGE', '维护资金流水', NULL,
     NULL, 'finance:fund:manage', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5043, 5041, 'BUTTON', 'FINANCE_FUND_RECONCILE', '资金对账', NULL,
     NULL, 'finance:fund:reconcile', 2, 1, 'ACTIVE', 0, 0, 0, 0)
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
    (7129, 3002, 5041, 0),
    (7130, 3002, 5042, 0),
    (7131, 3002, 5043, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
