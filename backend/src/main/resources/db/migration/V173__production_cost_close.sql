-- V173: production cost close and reversible variance settlement.

ALTER TABLE prd_order ADD COLUMN cost_close_status VARCHAR(16) NOT NULL DEFAULT 'OPEN';
ALTER TABLE prd_order ADD COLUMN settled_variance_amount DECIMAL(18, 2) NOT NULL DEFAULT 0;
ALTER TABLE prd_order ADD COLUMN cost_close_entry_id BIGINT NULL;
ALTER TABLE prd_order ADD COLUMN cost_closed_date DATE NULL;
ALTER TABLE prd_order ADD COLUMN cost_closed_by BIGINT NULL;
ALTER TABLE prd_order ADD COLUMN cost_closed_time TIMESTAMP NULL;

CREATE TABLE prd_cost_close (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    close_no VARCHAR(64) NOT NULL,
    order_id BIGINT NOT NULL,
    business_date DATE NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'POSTED',
    reversal_of_close_id BIGINT NULL,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    reason VARCHAR(255),
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_prd_cost_close_status CHECK (status IN ('POSTED', 'REVERSED'))
);

CREATE UNIQUE INDEX uk_prd_cost_close_scope_no
    ON prd_cost_close (company_id, account_book_id, close_no);
CREATE UNIQUE INDEX uk_prd_cost_close_scope_reversal
    ON prd_cost_close (company_id, account_book_id, reversal_of_close_id);
CREATE INDEX idx_prd_cost_close_scope_order_date
    ON prd_cost_close (company_id, account_book_id, order_id, business_date);

INSERT INTO sys_sequence_rule
(id, company_id, account_book_id, biz_type, prefix, date_pattern, seq_length, current_value,
 status, created_by, updated_by, version)
SELECT candidate.next_id,
       1, 1, 'PRODUCTION_COST_CLOSE', 'PCC', 'yyyyMMdd', 4, 0,
       'ACTIVE', 0, 0, 0
FROM (
    SELECT COALESCE(MAX(id), 2037) + 1 AS next_id
    FROM sys_sequence_rule
) candidate
WHERE NOT EXISTS (
    SELECT 1 FROM sys_sequence_rule
    WHERE company_id = 1 AND account_book_id = 1 AND biz_type = 'PRODUCTION_COST_CLOSE'
);

INSERT INTO fin_account_subject
(id, company_id, account_book_id, subject_code, subject_name, parent_id, subject_type, balance_direction,
 status, deleted_flag, remark, created_by, updated_by, version)
SELECT candidate.next_id,
       1, 1, '5102', '生产成本差异', NULL, 'EXPENSE', 'DEBIT',
       'ACTIVE', 0, '生产工单成本关单差异结转科目', 0, 0, 0
FROM (
    SELECT GREATEST(COALESCE(MAX(id), 0), 910013) + 1 AS next_id
    FROM fin_account_subject
) candidate
WHERE NOT EXISTS (
    SELECT 1 FROM fin_account_subject
    WHERE company_id = 1 AND account_book_id = 1 AND subject_code = '5102'
);

INSERT INTO sys_menu
    (id, parent_id, menu_type, menu_code, menu_name, permission, sort_no, visible_flag,
     status, deleted_flag, created_by, updated_by, version)
SELECT allocated.base_id + 1, parent.id, 'BUTTON', 'PRODUCTION_ORDER_COST_CLOSE', '生产成本关单',
       'production:order:cost:close', 11, 1, 'ACTIVE', 0, 0, 0, 0
FROM (SELECT COALESCE(MAX(id), 0) AS base_id FROM sys_menu) allocated
JOIN sys_menu parent ON parent.permission = 'production:order:view'
    AND parent.menu_type = 'MENU' AND parent.deleted_flag = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu existing WHERE existing.menu_code = 'PRODUCTION_ORDER_COST_CLOSE'
);

INSERT INTO sys_menu
    (id, parent_id, menu_type, menu_code, menu_name, permission, sort_no, visible_flag,
     status, deleted_flag, created_by, updated_by, version)
SELECT allocated.base_id + 1, parent.id, 'BUTTON', 'PRODUCTION_ORDER_COST_REOPEN', '生产成本反关单',
       'production:order:cost:reopen', 12, 1, 'ACTIVE', 0, 0, 0, 0
FROM (SELECT COALESCE(MAX(id), 0) AS base_id FROM sys_menu) allocated
JOIN sys_menu parent ON parent.permission = 'production:order:view'
    AND parent.menu_type = 'MENU' AND parent.deleted_flag = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu existing WHERE existing.menu_code = 'PRODUCTION_ORDER_COST_REOPEN'
);

INSERT INTO sys_role_menu (id, role_id, menu_id, created_by)
SELECT allocated.base_id + ROW_NUMBER() OVER (ORDER BY roles.id, menus.id), roles.id, menus.id, 0
FROM (SELECT COALESCE(MAX(id), 0) AS base_id FROM sys_role_menu) allocated
CROSS JOIN sys_role roles
CROSS JOIN sys_menu menus
WHERE roles.role_code = 'ERP_ADMIN' AND roles.deleted_flag = 0
  AND menus.permission IN ('production:order:cost:close', 'production:order:cost:reopen')
  AND menus.deleted_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu existing
      WHERE existing.role_id = roles.id AND existing.menu_id = menus.id
  );
