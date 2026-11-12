-- V176: direct-method cash-flow item templates, cash-equivalent scope and counterpart mappings.

CREATE TABLE IF NOT EXISTS sys_cash_flow_item_template (
    id BIGINT PRIMARY KEY,
    formula_version VARCHAR(32) NOT NULL,
    item_code VARCHAR(64) NOT NULL,
    item_name VARCHAR(128) NOT NULL,
    activity_type VARCHAR(32) NOT NULL CHECK (activity_type IN ('OPERATING', 'INVESTING', 'FINANCING')),
    sort_no INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_cash_flow_template_version_item (formula_version, item_code)
);

CREATE TABLE IF NOT EXISTS fin_cash_equivalent_subject (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    formula_version VARCHAR(32) NOT NULL,
    subject_code VARCHAR(64) NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_cash_equivalent_subject
        (company_id, account_book_id, formula_version, subject_code)
);

CREATE TABLE IF NOT EXISTS fin_cash_flow_subject_map (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    formula_version VARCHAR(32) NOT NULL,
    item_code VARCHAR(64) NOT NULL,
    subject_code VARCHAR(64) NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_cash_flow_map_subject
        (company_id, account_book_id, formula_version, subject_code),
    KEY idx_cash_flow_map_item
        (company_id, account_book_id, formula_version, item_code)
);

INSERT INTO sys_cash_flow_item_template
    (id, formula_version, item_code, item_name, activity_type, sort_no, status, created_by, updated_by, version)
VALUES
    (977001, 'CASH_FLOW_DIRECT_V1', 'CFO_CUSTOMER_RECEIPTS', '销售商品及提供劳务收到的现金', 'OPERATING', 10, 'ACTIVE', 0, 0, 0),
    (977002, 'CASH_FLOW_DIRECT_V1', 'CFO_SUPPLIER_PAYMENTS', '购买商品及接受劳务支付的现金', 'OPERATING', 20, 'ACTIVE', 0, 0, 0),
    (977003, 'CASH_FLOW_DIRECT_V1', 'CFO_EMPLOYEE_TAX', '支付给职工以及支付的各项税费', 'OPERATING', 30, 'ACTIVE', 0, 0, 0),
    (977004, 'CASH_FLOW_DIRECT_V1', 'CFO_OPERATING_EXPENSES', '支付的其他经营活动现金', 'OPERATING', 40, 'ACTIVE', 0, 0, 0),
    (977005, 'CASH_FLOW_DIRECT_V1', 'CFO_OTHER', '收到的其他经营活动现金', 'OPERATING', 90, 'ACTIVE', 0, 0, 0),
    (977006, 'CASH_FLOW_DIRECT_V1', 'CFI_ASSET_PURCHASE', '购建长期资产支付的现金', 'INVESTING', 110, 'ACTIVE', 0, 0, 0),
    (977007, 'CASH_FLOW_DIRECT_V1', 'CFI_ASSET_DISPOSAL', '处置长期资产收到的现金', 'INVESTING', 120, 'ACTIVE', 0, 0, 0),
    (977008, 'CASH_FLOW_DIRECT_V1', 'CFI_OTHER', '其他投资活动现金流量', 'INVESTING', 190, 'ACTIVE', 0, 0, 0),
    (977009, 'CASH_FLOW_DIRECT_V1', 'CFF_CAPITAL_BORROWING', '吸收投资及借款收到的现金', 'FINANCING', 210, 'ACTIVE', 0, 0, 0),
    (977010, 'CASH_FLOW_DIRECT_V1', 'CFF_DEBT_REPAYMENT', '偿还债务支付的现金', 'FINANCING', 220, 'ACTIVE', 0, 0, 0),
    (977011, 'CASH_FLOW_DIRECT_V1', 'CFF_INTEREST_DIVIDEND', '分配股利、利润或偿付利息支付的现金', 'FINANCING', 230, 'ACTIVE', 0, 0, 0),
    (977012, 'CASH_FLOW_DIRECT_V1', 'CFF_OTHER', '其他筹资活动现金流量', 'FINANCING', 290, 'ACTIVE', 0, 0, 0) AS new
ON DUPLICATE KEY UPDATE
    item_name = new.item_name,
    activity_type = new.activity_type,
    sort_no = new.sort_no,
    status = new.status,
    updated_by = new.updated_by;

INSERT INTO fin_cash_equivalent_subject
    (id, company_id, account_book_id, formula_version, subject_code, created_by, updated_by, version)
VALUES
    (978001, 1, 1, 'CASH_FLOW_DIRECT_V1', '1002', 0, 0, 0) AS new
ON DUPLICATE KEY UPDATE
    updated_by = new.updated_by;

INSERT INTO fin_cash_flow_subject_map
    (id, company_id, account_book_id, formula_version, item_code, subject_code, created_by, updated_by, version)
VALUES
    (979001, 1, 1, 'CASH_FLOW_DIRECT_V1', 'CFO_CUSTOMER_RECEIPTS', '1122', 0, 0, 0),
    (979002, 1, 1, 'CASH_FLOW_DIRECT_V1', 'CFO_CUSTOMER_RECEIPTS', '2203', 0, 0, 0),
    (979003, 1, 1, 'CASH_FLOW_DIRECT_V1', 'CFO_CUSTOMER_RECEIPTS', '6001', 0, 0, 0),
    (979004, 1, 1, 'CASH_FLOW_DIRECT_V1', 'CFO_SUPPLIER_PAYMENTS', '2202', 0, 0, 0),
    (979005, 1, 1, 'CASH_FLOW_DIRECT_V1', 'CFO_SUPPLIER_PAYMENTS', '1123', 0, 0, 0),
    (979006, 1, 1, 'CASH_FLOW_DIRECT_V1', 'CFO_EMPLOYEE_TAX', '2211', 0, 0, 0),
    (979007, 1, 1, 'CASH_FLOW_DIRECT_V1', 'CFO_EMPLOYEE_TAX', '222101', 0, 0, 0),
    (979008, 1, 1, 'CASH_FLOW_DIRECT_V1', 'CFO_OPERATING_EXPENSES', '5101', 0, 0, 0),
    (979009, 1, 1, 'CASH_FLOW_DIRECT_V1', 'CFO_OPERATING_EXPENSES', '5102', 0, 0, 0),
    (979010, 1, 1, 'CASH_FLOW_DIRECT_V1', 'CFO_OPERATING_EXPENSES', '6602', 0, 0, 0),
    (979011, 1, 1, 'CASH_FLOW_DIRECT_V1', 'CFF_INTEREST_DIVIDEND', '6061', 0, 0, 0) AS new
ON DUPLICATE KEY UPDATE
    item_code = new.item_code,
    updated_by = new.updated_by;
