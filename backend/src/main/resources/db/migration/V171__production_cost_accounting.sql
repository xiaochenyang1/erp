-- V171: production cost accounting 1.0.
-- Material, labor and overhead are accumulated separately, allocated by completion quantity,
-- snapshotted on completion/reversal documents, and reconciled to subject 5001.

ALTER TABLE prd_order ADD COLUMN labor_amount DECIMAL(18, 2) NOT NULL DEFAULT 0;
ALTER TABLE prd_order ADD COLUMN overhead_amount DECIMAL(18, 2) NOT NULL DEFAULT 0;
ALTER TABLE prd_order ADD COLUMN finished_material_amount DECIMAL(18, 2) NOT NULL DEFAULT 0;
ALTER TABLE prd_order ADD COLUMN finished_labor_amount DECIMAL(18, 2) NOT NULL DEFAULT 0;
ALTER TABLE prd_order ADD COLUMN finished_overhead_amount DECIMAL(18, 2) NOT NULL DEFAULT 0;

ALTER TABLE prd_completion ADD COLUMN material_cost_amount DECIMAL(18, 2) NOT NULL DEFAULT 0;
ALTER TABLE prd_completion ADD COLUMN labor_cost_amount DECIMAL(18, 2) NOT NULL DEFAULT 0;
ALTER TABLE prd_completion ADD COLUMN overhead_cost_amount DECIMAL(18, 2) NOT NULL DEFAULT 0;

ALTER TABLE prd_completion_reversal ADD COLUMN material_cost_amount DECIMAL(18, 2) NOT NULL DEFAULT 0;
ALTER TABLE prd_completion_reversal ADD COLUMN labor_cost_amount DECIMAL(18, 2) NOT NULL DEFAULT 0;
ALTER TABLE prd_completion_reversal ADD COLUMN overhead_cost_amount DECIMAL(18, 2) NOT NULL DEFAULT 0;

-- Historical production only carried material cost. Preserve every published total exactly.
UPDATE prd_order
SET finished_material_amount = finished_amount;

UPDATE prd_completion
SET material_cost_amount = completed_amount;

UPDATE prd_completion_reversal
SET material_cost_amount = reversed_amount;

CREATE TABLE prd_cost_entry (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    cost_no VARCHAR(64) NOT NULL,
    order_id BIGINT NOT NULL,
    operation_id BIGINT,
    cost_type VARCHAR(16) NOT NULL,
    business_date DATE NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'POSTED',
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_prd_cost_entry_type CHECK (cost_type IN ('LABOR', 'OVERHEAD')),
    CONSTRAINT chk_prd_cost_entry_status CHECK (status IN ('POSTED')),
    CONSTRAINT chk_prd_cost_entry_amount CHECK (amount > 0)
);

CREATE UNIQUE INDEX uk_prd_cost_entry_scope_no
    ON prd_cost_entry (company_id, account_book_id, cost_no);
CREATE INDEX idx_prd_cost_entry_scope_order_date
    ON prd_cost_entry (company_id, account_book_id, order_id, business_date);
CREATE INDEX idx_prd_cost_entry_scope_operation
    ON prd_cost_entry (company_id, account_book_id, operation_id, business_date);

INSERT INTO sys_sequence_rule
(id, company_id, account_book_id, biz_type, prefix, date_pattern, seq_length, current_value,
 status, created_by, updated_by, version)
SELECT candidate.next_id,
       1, 1, 'PRODUCTION_COST_ENTRY', 'PCE', 'yyyyMMdd', 4, 0,
       'ACTIVE', 0, 0, 0
FROM (
    SELECT COALESCE(MAX(id), 2036) + 1 AS next_id
    FROM sys_sequence_rule
) candidate
WHERE NOT EXISTS (
    SELECT 1 FROM sys_sequence_rule
    WHERE company_id = 1 AND account_book_id = 1 AND biz_type = 'PRODUCTION_COST_ENTRY'
);

-- Labor accrual: Dr production cost / Cr employee compensation payable.
INSERT INTO fin_account_subject
(id, company_id, account_book_id, subject_code, subject_name, parent_id, subject_type, balance_direction,
 status, deleted_flag, remark, created_by, updated_by, version)
SELECT candidate.next_id,
       1, 1, '2211', '应付职工薪酬', NULL, 'LIABILITY', 'CREDIT',
       'ACTIVE', 0, '生产人工成本归集贷方科目', 0, 0, 0
FROM (
    SELECT GREATEST(COALESCE(MAX(id), 0), 910011) + 1 AS next_id
    FROM fin_account_subject
) candidate
WHERE NOT EXISTS (
    SELECT 1 FROM fin_account_subject
    WHERE company_id = 1 AND account_book_id = 1 AND subject_code = '2211'
);

-- Overhead allocation: Dr production cost / Cr manufacturing overhead.
INSERT INTO fin_account_subject
(id, company_id, account_book_id, subject_code, subject_name, parent_id, subject_type, balance_direction,
 status, deleted_flag, remark, created_by, updated_by, version)
SELECT candidate.next_id,
       1, 1, '5101', '制造费用', NULL, 'EXPENSE', 'DEBIT',
       'ACTIVE', 0, '生产制造费用分配贷方科目', 0, 0, 0
FROM (
    SELECT GREATEST(COALESCE(MAX(id), 0), 910012) + 1 AS next_id
    FROM fin_account_subject
) candidate
WHERE NOT EXISTS (
    SELECT 1 FROM fin_account_subject
    WHERE company_id = 1 AND account_book_id = 1 AND subject_code = '5101'
);

-- Resolve the production-order parent and ERP_ADMIN roles by business key.
INSERT INTO sys_menu
    (id, parent_id, menu_type, menu_code, menu_name, permission, sort_no, visible_flag,
     status, deleted_flag, created_by, updated_by, version)
SELECT allocated.base_id + 1, parent.id, 'BUTTON', 'PRODUCTION_ORDER_COST', '生产成本归集',
       'production:order:cost', 9, 1, 'ACTIVE', 0, 0, 0, 0
FROM (SELECT COALESCE(MAX(id), 0) AS base_id FROM sys_menu) allocated
JOIN sys_menu parent ON parent.permission = 'production:order:view'
    AND parent.menu_type = 'MENU' AND parent.deleted_flag = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu existing WHERE existing.menu_code = 'PRODUCTION_ORDER_COST'
);

INSERT INTO sys_role_menu (id, role_id, menu_id, created_by)
SELECT allocated.base_id + ROW_NUMBER() OVER (ORDER BY roles.id, menus.id), roles.id, menus.id, 0
FROM (SELECT COALESCE(MAX(id), 0) AS base_id FROM sys_role_menu) allocated
CROSS JOIN sys_role roles
CROSS JOIN sys_menu menus
WHERE roles.role_code = 'ERP_ADMIN' AND roles.deleted_flag = 0
  AND menus.permission = 'production:order:cost' AND menus.deleted_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu existing
      WHERE existing.role_id = roles.id AND existing.menu_id = menus.id
  );
