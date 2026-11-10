-- V172: immutable reversal and correction chain for production cost entries.

ALTER TABLE prd_cost_entry
    ADD COLUMN reversal_of_entry_id BIGINT NULL AFTER status,
    ADD COLUMN correction_of_entry_id BIGINT NULL AFTER reversal_of_entry_id;

ALTER TABLE prd_cost_entry DROP CHECK chk_prd_cost_entry_status;
ALTER TABLE prd_cost_entry
    ADD CONSTRAINT chk_prd_cost_entry_status CHECK (status IN ('POSTED', 'REVERSED'));

ALTER TABLE prd_cost_entry DROP CHECK chk_prd_cost_entry_amount;
ALTER TABLE prd_cost_entry
    ADD CONSTRAINT chk_prd_cost_entry_amount CHECK (amount <> 0);

CREATE UNIQUE INDEX uk_prd_cost_entry_scope_reversal
    ON prd_cost_entry (company_id, account_book_id, reversal_of_entry_id);
CREATE UNIQUE INDEX uk_prd_cost_entry_scope_correction
    ON prd_cost_entry (company_id, account_book_id, correction_of_entry_id);
CREATE INDEX idx_prd_cost_entry_scope_relations
    ON prd_cost_entry (company_id, account_book_id, order_id, reversal_of_entry_id, correction_of_entry_id);

INSERT INTO sys_menu
    (id, parent_id, menu_type, menu_code, menu_name, permission, sort_no, visible_flag,
     status, deleted_flag, created_by, updated_by, version)
SELECT allocated.base_id + 1, parent.id, 'BUTTON', 'PRODUCTION_ORDER_COST_REVERSE', '生产成本归集冲销',
       'production:order:cost:reverse', 10, 1, 'ACTIVE', 0, 0, 0, 0
FROM (SELECT COALESCE(MAX(id), 0) AS base_id FROM sys_menu) allocated
JOIN sys_menu parent ON parent.permission = 'production:order:view'
    AND parent.menu_type = 'MENU' AND parent.deleted_flag = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu existing WHERE existing.menu_code = 'PRODUCTION_ORDER_COST_REVERSE'
);

INSERT INTO sys_role_menu (id, role_id, menu_id, created_by)
SELECT allocated.base_id + ROW_NUMBER() OVER (ORDER BY roles.id, menus.id), roles.id, menus.id, 0
FROM (SELECT COALESCE(MAX(id), 0) AS base_id FROM sys_role_menu) allocated
CROSS JOIN sys_role roles
CROSS JOIN sys_menu menus
WHERE roles.role_code = 'ERP_ADMIN' AND roles.deleted_flag = 0
  AND menus.permission = 'production:order:cost:reverse' AND menus.deleted_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu existing
      WHERE existing.role_id = roles.id AND existing.menu_id = menus.id
  );
