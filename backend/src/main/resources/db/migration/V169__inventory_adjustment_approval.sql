-- Existing posted inventory facts remain unchanged. Every unposted adjustment now requires approval.
ALTER TABLE inv_adjustment DROP CHECK inv_adjustment_chk_1;
ALTER TABLE inv_adjustment ADD CONSTRAINT inv_adjustment_chk_1
    CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'POSTED', 'CANCELLED'));
ALTER TABLE inv_stock_check DROP CHECK inv_stock_check_chk_1;
ALTER TABLE inv_stock_check ADD CONSTRAINT inv_stock_check_chk_1
    CHECK (status IN ('DRAFT', 'COUNTED', 'ADJUSTMENT_PENDING', 'ADJUSTED', 'CANCELLED'));

-- Allocate from current maxima, and resolve parents/roles by business key, never by historical seed IDs.
INSERT INTO sys_menu
    (id, parent_id, menu_type, menu_code, menu_name, permission, sort_no, visible_flag,
     status, deleted_flag, created_by, updated_by, version)
SELECT allocated.base_id + buttons.offset_id, parent.id, 'BUTTON', buttons.menu_code,
       buttons.menu_name, buttons.permission, buttons.offset_id + 10, 1, 'ACTIVE', 0, 0, 0, 0
FROM (SELECT COALESCE(MAX(id), 0) AS base_id FROM sys_menu) allocated
CROSS JOIN (
    SELECT 1 AS offset_id, 'INVENTORY_ADJUSTMENT_UPDATE' AS menu_code, '编辑库存调整单' AS menu_name,
           'inventory:adjustment:update' AS permission
    UNION ALL SELECT 2, 'INVENTORY_ADJUSTMENT_SUBMIT', '提交库存调整审批', 'inventory:adjustment:submit'
    UNION ALL SELECT 3, 'INVENTORY_ADJUSTMENT_APPROVE', '审批库存调整单', 'inventory:adjustment:approve'
) buttons
JOIN sys_menu parent ON parent.permission = 'inventory:adjustment:view'
    AND parent.menu_type = 'MENU' AND parent.deleted_flag = 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.menu_code = buttons.menu_code);

INSERT INTO sys_role_menu (id, role_id, menu_id, created_by)
SELECT allocated.base_id + ROW_NUMBER() OVER (ORDER BY roles.id, menus.id), roles.id, menus.id, 0
FROM (SELECT COALESCE(MAX(id), 0) AS base_id FROM sys_role_menu) allocated
CROSS JOIN sys_role roles
CROSS JOIN sys_menu menus
WHERE roles.role_code = 'ERP_ADMIN' AND roles.deleted_flag = 0
  AND menus.permission IN ('inventory:adjustment:update', 'inventory:adjustment:submit', 'inventory:adjustment:approve')
  AND menus.deleted_flag = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu existing WHERE existing.role_id = roles.id AND existing.menu_id = menus.id);

-- Seed one approval node for each current ERP_ADMIN account book. Other roles are explicitly assigned by administrators.
INSERT INTO wf_approval_config
    (id, company_id, account_book_id, business_type, config_name, status, task_timeout_hours,
     deleted_flag, remark, created_by, updated_by, version)
SELECT allocated.base_id + ROW_NUMBER() OVER (ORDER BY books.company_id, books.account_book_id),
       books.company_id, books.account_book_id, 'INVENTORY_ADJUSTMENT', '库存调整审批', 'ACTIVE', 24,
       0, 'V169 inventory adjustment approval', 0, 0, 0
FROM (SELECT COALESCE(MAX(id), 0) AS base_id FROM wf_approval_config) allocated
CROSS JOIN (SELECT DISTINCT company_id, account_book_id FROM sys_role
            WHERE role_code = 'ERP_ADMIN' AND deleted_flag = 0) books
WHERE NOT EXISTS (SELECT 1 FROM wf_approval_config existing
                  WHERE existing.company_id = books.company_id AND existing.account_book_id = books.account_book_id
                    AND existing.business_type = 'INVENTORY_ADJUSTMENT' AND existing.deleted_flag = 0);

INSERT INTO wf_approval_node
    (id, company_id, config_id, node_name, node_order, approval_mode, status, created_by, updated_by, version)
SELECT allocated.base_id + ROW_NUMBER() OVER (ORDER BY configs.id), configs.company_id, configs.id,
       '库存调整审核', 1, 'ANY', 'ACTIVE', 0, 0, 0
FROM (SELECT COALESCE(MAX(id), 0) AS base_id FROM wf_approval_node) allocated
CROSS JOIN wf_approval_config configs
WHERE configs.business_type = 'INVENTORY_ADJUSTMENT' AND configs.deleted_flag = 0
  AND configs.remark = 'V169 inventory adjustment approval'
  AND NOT EXISTS (SELECT 1 FROM wf_approval_node existing WHERE existing.config_id = configs.id);

INSERT INTO wf_approval_node_approver
    (id, company_id, node_id, approver_type, approver_id, created_by, updated_by, version)
SELECT allocated.base_id + ROW_NUMBER() OVER (ORDER BY nodes.id, roles.id),
       configs.company_id, nodes.id, 'ROLE', roles.id, 0, 0, 0
FROM (SELECT COALESCE(MAX(id), 0) AS base_id FROM wf_approval_node_approver) allocated
CROSS JOIN wf_approval_node nodes
JOIN wf_approval_config configs ON configs.id = nodes.config_id AND configs.company_id = nodes.company_id
JOIN sys_role roles ON roles.company_id = configs.company_id AND roles.account_book_id = configs.account_book_id
WHERE configs.business_type = 'INVENTORY_ADJUSTMENT' AND configs.deleted_flag = 0
  AND configs.remark = 'V169 inventory adjustment approval'
  AND roles.role_code = 'ERP_ADMIN' AND roles.deleted_flag = 0
  AND NOT EXISTS (SELECT 1 FROM wf_approval_node_approver existing
                  WHERE existing.node_id = nodes.id AND existing.approver_type = 'ROLE' AND existing.approver_id = roles.id);
