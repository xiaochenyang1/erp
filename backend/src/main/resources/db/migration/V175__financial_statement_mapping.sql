-- V175: versioned financial-statement item templates and tenant/account-book subject mappings.

CREATE TABLE IF NOT EXISTS sys_financial_statement_item_template (
    id BIGINT PRIMARY KEY,
    formula_version VARCHAR(32) NOT NULL,
    statement_type VARCHAR(32) NOT NULL CHECK (statement_type IN ('BALANCE_SHEET', 'INCOME_STATEMENT')),
    item_code VARCHAR(64) NOT NULL,
    item_name VARCHAR(128) NOT NULL,
    section_code VARCHAR(32) NOT NULL CHECK (section_code IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE')),
    amount_direction VARCHAR(16) NOT NULL CHECK (amount_direction IN ('DEBIT', 'CREDIT')),
    formula_code VARCHAR(64),
    sort_no INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_fin_statement_template_version_item (formula_version, statement_type, item_code)
);

CREATE TABLE IF NOT EXISTS fin_financial_statement_subject_map (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    formula_version VARCHAR(32) NOT NULL,
    statement_type VARCHAR(32) NOT NULL CHECK (statement_type IN ('BALANCE_SHEET', 'INCOME_STATEMENT')),
    item_code VARCHAR(64) NOT NULL,
    subject_code VARCHAR(64) NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_fin_statement_map_subject
        (company_id, account_book_id, formula_version, statement_type, subject_code),
    KEY idx_fin_statement_map_item
        (company_id, account_book_id, formula_version, statement_type, item_code)
);

INSERT INTO sys_financial_statement_item_template
    (id, formula_version, statement_type, item_code, item_name, section_code, amount_direction,
     formula_code, sort_no, status, created_by, updated_by, version)
VALUES
    (975001, 'TYPE_MAPPING_V1', 'BALANCE_SHEET', 'BS_CASH_BANK', '货币资金', 'ASSET', 'DEBIT', NULL, 10, 'ACTIVE', 0, 0, 0),
    (975002, 'TYPE_MAPPING_V1', 'BALANCE_SHEET', 'BS_INVENTORY', '存货及在制品', 'ASSET', 'DEBIT', NULL, 20, 'ACTIVE', 0, 0, 0),
    (975003, 'TYPE_MAPPING_V1', 'BALANCE_SHEET', 'BS_RECEIVABLES', '应收及预付款项', 'ASSET', 'DEBIT', NULL, 30, 'ACTIVE', 0, 0, 0),
    (975004, 'TYPE_MAPPING_V1', 'BALANCE_SHEET', 'BS_OTHER_ASSETS', '其他资产', 'ASSET', 'DEBIT', NULL, 90, 'ACTIVE', 0, 0, 0),
    (975005, 'TYPE_MAPPING_V1', 'BALANCE_SHEET', 'BS_PAYABLES', '应付及预收款项', 'LIABILITY', 'CREDIT', NULL, 110, 'ACTIVE', 0, 0, 0),
    (975006, 'TYPE_MAPPING_V1', 'BALANCE_SHEET', 'BS_EMPLOYEE_TAX', '职工薪酬及税费', 'LIABILITY', 'CREDIT', NULL, 120, 'ACTIVE', 0, 0, 0),
    (975007, 'TYPE_MAPPING_V1', 'BALANCE_SHEET', 'BS_OTHER_LIABILITIES', '其他负债', 'LIABILITY', 'CREDIT', NULL, 190, 'ACTIVE', 0, 0, 0),
    (975008, 'TYPE_MAPPING_V1', 'BALANCE_SHEET', 'BS_EQUITY', '实收资本及其他权益', 'EQUITY', 'CREDIT', NULL, 210, 'ACTIVE', 0, 0, 0),
    (975009, 'TYPE_MAPPING_V1', 'BALANCE_SHEET', 'BS_CURRENT_PROFIT', '累计未结转损益', 'EQUITY', 'CREDIT', 'CUMULATIVE_PROFIT', 220, 'ACTIVE', 0, 0, 0),
    (975010, 'TYPE_MAPPING_V1', 'INCOME_STATEMENT', 'IS_OPERATING_REVENUE', '主营业务收入', 'REVENUE', 'CREDIT', NULL, 10, 'ACTIVE', 0, 0, 0),
    (975011, 'TYPE_MAPPING_V1', 'INCOME_STATEMENT', 'IS_REVENUE_DEDUCTION', '收入扣减', 'REVENUE', 'CREDIT', NULL, 20, 'ACTIVE', 0, 0, 0),
    (975012, 'TYPE_MAPPING_V1', 'INCOME_STATEMENT', 'IS_OPERATING_COST', '主营业务成本', 'EXPENSE', 'DEBIT', NULL, 110, 'ACTIVE', 0, 0, 0),
    (975013, 'TYPE_MAPPING_V1', 'INCOME_STATEMENT', 'IS_MANUFACTURING_EXPENSE', '制造费用及成本差异', 'EXPENSE', 'DEBIT', NULL, 120, 'ACTIVE', 0, 0, 0),
    (975014, 'TYPE_MAPPING_V1', 'INCOME_STATEMENT', 'IS_ADMIN_EXPENSE', '管理费用', 'EXPENSE', 'DEBIT', NULL, 130, 'ACTIVE', 0, 0, 0),
    (975015, 'TYPE_MAPPING_V1', 'INCOME_STATEMENT', 'IS_FINANCE_EXPENSE', '财务费用', 'EXPENSE', 'DEBIT', NULL, 140, 'ACTIVE', 0, 0, 0),
    (975016, 'TYPE_MAPPING_V1', 'INCOME_STATEMENT', 'IS_OTHER_REVENUE', '其他收入', 'REVENUE', 'CREDIT', NULL, 90, 'ACTIVE', 0, 0, 0),
    (975017, 'TYPE_MAPPING_V1', 'INCOME_STATEMENT', 'IS_OTHER_EXPENSE', '其他费用', 'EXPENSE', 'DEBIT', NULL, 190, 'ACTIVE', 0, 0, 0) AS new
ON DUPLICATE KEY UPDATE
    item_name = new.item_name,
    section_code = new.section_code,
    amount_direction = new.amount_direction,
    formula_code = new.formula_code,
    sort_no = new.sort_no,
    status = new.status,
    updated_by = new.updated_by;

INSERT INTO fin_financial_statement_subject_map
    (id, company_id, account_book_id, formula_version, statement_type, item_code, subject_code,
     created_by, updated_by, version)
VALUES
    (976001, 1, 1, 'TYPE_MAPPING_V1', 'BALANCE_SHEET', 'BS_CASH_BANK', '1002', 0, 0, 0),
    (976002, 1, 1, 'TYPE_MAPPING_V1', 'BALANCE_SHEET', 'BS_INVENTORY', '1001', 0, 0, 0),
    (976003, 1, 1, 'TYPE_MAPPING_V1', 'BALANCE_SHEET', 'BS_INVENTORY', '5001', 0, 0, 0),
    (976004, 1, 1, 'TYPE_MAPPING_V1', 'BALANCE_SHEET', 'BS_RECEIVABLES', '1122', 0, 0, 0),
    (976005, 1, 1, 'TYPE_MAPPING_V1', 'BALANCE_SHEET', 'BS_RECEIVABLES', '1123', 0, 0, 0),
    (976006, 1, 1, 'TYPE_MAPPING_V1', 'BALANCE_SHEET', 'BS_PAYABLES', '2202', 0, 0, 0),
    (976007, 1, 1, 'TYPE_MAPPING_V1', 'BALANCE_SHEET', 'BS_PAYABLES', '2203', 0, 0, 0),
    (976008, 1, 1, 'TYPE_MAPPING_V1', 'BALANCE_SHEET', 'BS_EMPLOYEE_TAX', '2211', 0, 0, 0),
    (976009, 1, 1, 'TYPE_MAPPING_V1', 'BALANCE_SHEET', 'BS_EMPLOYEE_TAX', '222101', 0, 0, 0),
    (976010, 1, 1, 'TYPE_MAPPING_V1', 'INCOME_STATEMENT', 'IS_OPERATING_REVENUE', '6001', 0, 0, 0),
    (976011, 1, 1, 'TYPE_MAPPING_V1', 'INCOME_STATEMENT', 'IS_REVENUE_DEDUCTION', '6401', 0, 0, 0),
    (976012, 1, 1, 'TYPE_MAPPING_V1', 'INCOME_STATEMENT', 'IS_OPERATING_COST', '6402', 0, 0, 0),
    (976013, 1, 1, 'TYPE_MAPPING_V1', 'INCOME_STATEMENT', 'IS_MANUFACTURING_EXPENSE', '5101', 0, 0, 0),
    (976014, 1, 1, 'TYPE_MAPPING_V1', 'INCOME_STATEMENT', 'IS_MANUFACTURING_EXPENSE', '5102', 0, 0, 0),
    (976015, 1, 1, 'TYPE_MAPPING_V1', 'INCOME_STATEMENT', 'IS_ADMIN_EXPENSE', '6602', 0, 0, 0),
    (976016, 1, 1, 'TYPE_MAPPING_V1', 'INCOME_STATEMENT', 'IS_FINANCE_EXPENSE', '6061', 0, 0, 0) AS new
ON DUPLICATE KEY UPDATE
    item_code = new.item_code,
    updated_by = new.updated_by;
