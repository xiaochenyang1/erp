-- V148: 商务合同台账最小闭环（销售/采购合同、明细与生命周期）
CREATE TABLE IF NOT EXISTS biz_contract (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    contract_no VARCHAR(64) NOT NULL,
    contract_type VARCHAR(16) NOT NULL,
    customer_id BIGINT NULL,
    supplier_id BIGINT NULL,
    contract_name VARCHAR(160) NOT NULL,
    signed_date DATE NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_biz_contract_type CHECK (contract_type IN ('SALES', 'PURCHASE')),
    CONSTRAINT chk_biz_contract_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'REJECTED', 'ACTIVE', 'CLOSED', 'CANCELLED')),
    CONSTRAINT chk_biz_contract_party CHECK ((contract_type = 'SALES' AND customer_id IS NOT NULL AND supplier_id IS NULL) OR (contract_type = 'PURCHASE' AND supplier_id IS NOT NULL AND customer_id IS NULL))
);

CREATE TABLE IF NOT EXISTS biz_contract_line (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    contract_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity DECIMAL(18,4) NOT NULL,
    fulfilled_quantity DECIMAL(18,4) NOT NULL DEFAULT 0,
    unit_price DECIMAL(18,2) NOT NULL DEFAULT 0,
    amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_biz_contract_company_book_no ON biz_contract (company_id, account_book_id, contract_no);
CREATE INDEX idx_biz_contract_company_book_type_status ON biz_contract (company_id, account_book_id, contract_type, status, deleted_flag);
CREATE INDEX idx_biz_contract_company_book_party ON biz_contract (company_id, account_book_id, customer_id, supplier_id, status);
CREATE UNIQUE INDEX uk_biz_contract_line_scope ON biz_contract_line (company_id, account_book_id, contract_id, line_no);
CREATE INDEX idx_biz_contract_line_contract ON biz_contract_line (company_id, account_book_id, contract_id, deleted_flag);

INSERT INTO sys_sequence_rule
(id, company_id, account_book_id, biz_type, prefix, date_pattern, seq_length, current_value, status, created_by, updated_by, version)
VALUES (2036, 1, 1, 'COMMERCIAL_CONTRACT', 'CT', 'yyyyMMdd', 4, 0, 'ACTIVE', 0, 0, 0)
ON DUPLICATE KEY UPDATE prefix = VALUES(prefix), date_pattern = VALUES(date_pattern), seq_length = VALUES(seq_length), status = VALUES(status);

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag, status, deleted_flag, created_by, updated_by, version)
VALUES
    (5500, 0, 'CATALOG', 'CONTRACT', '合同管理', NULL, 'Layout', NULL, 6, 1, 'ACTIVE', 0, 0, 0, 0),
    (5501, 5500, 'MENU', 'CONTRACT_CENTER', '合同台账', '/contracts', 'commercial/contracts/index', 'contract:view', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5502, 5501, 'BUTTON', 'CONTRACT_MANAGE', '合同维护', NULL, NULL, 'contract:manage', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5503, 5501, 'BUTTON', 'CONTRACT_APPROVE', '合同审批', NULL, NULL, 'contract:approve', 2, 1, 'ACTIVE', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE parent_id = VALUES(parent_id), menu_type = VALUES(menu_type), menu_name = VALUES(menu_name), path = VALUES(path), component = VALUES(component), permission = VALUES(permission), sort_no = VALUES(sort_no), status = VALUES(status), deleted_flag = VALUES(deleted_flag), updated_by = VALUES(updated_by);

INSERT INTO sys_role_menu (id, role_id, menu_id, created_by)
VALUES (7510, 3002, 5500, 0), (7511, 3002, 5501, 0), (7512, 3002, 5502, 0), (7513, 3002, 5503, 0)
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id), menu_id = VALUES(menu_id);
