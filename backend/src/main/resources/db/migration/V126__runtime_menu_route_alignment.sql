-- 收口运行时菜单与前端静态路由的最终差异。
-- 前端以静态路由注册组件，后端 runtime-menu-tree 只负责提供可见 path 白名单；
-- 因此聚合到现有页面的历史伪路由应保留授权节点但不再作为可见页面下发。

-- 质量管理在前端是独立顶层分组，不能继续挂在采购目录下。
INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5423, 0, 'CATALOG', 'QC', '质量管理', '/qc', 'Layout', NULL, 6, 1,
     'ACTIVE', 0, 0, 0, 0)
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

UPDATE sys_menu
SET parent_id = 5423,
    updated_by = 0
WHERE menu_code = 'QC_INSPECTION'
  AND deleted_flag = 0;

-- 修复历史脚本引用 db/init 旧菜单 ID 造成的孤儿节点。
UPDATE sys_menu
SET parent_id = 5009,
    updated_by = 0
WHERE menu_code = 'INVENTORY_REPLENISHMENT'
  AND deleted_flag = 0;

UPDATE sys_menu
SET parent_id = 5136,
    updated_by = 0
WHERE menu_code = 'PURCHASE_ORDER_UNAPPROVE'
  AND deleted_flag = 0;

-- 库存预占已聚合到 /inventory/stocks；五类旧报表已聚合到 /reports 标签页；
-- 期初导入已聚合到 /system/imports。保留节点和 role_menu 绑定用于权限授权，隐藏伪页面入口。
UPDATE sys_menu
SET visible_flag = 0,
    updated_by = 0
WHERE menu_code IN (
    'INVENTORY_RESERVATION',
    'INVENTORY_RESERVATION_CHECK',
    'INVENTORY_RESERVATION_RELEASE',
    'REPORT_PURCHASE_ORDER',
    'REPORT_SALES_ORDER',
    'REPORT_INVENTORY_BALANCE',
    'REPORT_FINANCE_SETTLEMENT',
    'REPORT_INVENTORY_TRANSACTION',
    'IMPORT_CENTER',
    'INITIAL_IMPORT'
)
  AND deleted_flag = 0;

-- REPORT 仅作为目录节点；真正的页面入口由 REPORT_CENTER 提供。
UPDATE sys_menu
SET path = NULL,
    updated_by = 0
WHERE menu_code = 'REPORT'
  AND menu_type = 'CATALOG'
  AND deleted_flag = 0;

-- 早期种子使用了单数目录名，修正为前端实际存在的组件路径。
UPDATE sys_menu
SET component = CASE menu_code
    WHEN 'SYSTEM_USER' THEN 'system/users/index'
    WHEN 'SYSTEM_ROLE' THEN 'system/roles/index'
    WHEN 'SYSTEM_MENU' THEN 'system/menus/index'
    WHEN 'SYSTEM_DEPT' THEN 'system/depts/index'
    WHEN 'SYSTEM_POST' THEN 'system/posts/index'
    WHEN 'SYSTEM_DICT' THEN 'system/dicts/index'
    WHEN 'SYSTEM_LOG' THEN 'system/logs/index'
    WHEN 'WORKFLOW_TASK' THEN 'workflow/tasks/index'
    WHEN 'WORKFLOW_RECORD' THEN 'workflow/records/index'
    WHEN 'INVENTORY_TRANSFER' THEN 'inventory/transfers/index'
    WHEN 'SYSTEM_USER_SESSION' THEN 'system/user-sessions/index'
    WHEN 'FINANCE_EXPENSE' THEN 'finance/expenses/index'
    WHEN 'FINANCE_VOUCHER' THEN 'finance/vouchers/index'
    WHEN 'FINANCE_RECEIVABLE' THEN 'finance/receivables/index'
    WHEN 'FINANCE_PAYABLE' THEN 'finance/payables/index'
    WHEN 'FINANCE_FUND' THEN 'finance/funds/index'
    WHEN 'SYSTEM_ATTACHMENT' THEN 'system/attachments/index'
    WHEN 'SYSTEM_NOTIFICATION' THEN 'system/notifications/index'
    WHEN 'WORKFLOW_CONFIG' THEN 'workflow/configs/index'
    WHEN 'PRODUCTION_BOM' THEN 'production/boms/index'
    WHEN 'PRODUCTION_ORDER' THEN 'production/orders/index'
    WHEN 'PRODUCTION_WORK_CENTER' THEN 'production/work-centers/index'
    WHEN 'PRODUCTION_ROUTING' THEN 'production/routings/index'
    ELSE component
END,
    updated_by = 0
WHERE menu_code IN (
    'SYSTEM_USER', 'SYSTEM_ROLE', 'SYSTEM_MENU', 'SYSTEM_DEPT', 'SYSTEM_POST', 'SYSTEM_DICT', 'SYSTEM_LOG',
    'WORKFLOW_TASK', 'WORKFLOW_RECORD', 'INVENTORY_TRANSFER', 'SYSTEM_USER_SESSION',
    'FINANCE_EXPENSE', 'FINANCE_VOUCHER', 'FINANCE_RECEIVABLE', 'FINANCE_PAYABLE', 'FINANCE_FUND',
    'SYSTEM_ATTACHMENT', 'SYSTEM_NOTIFICATION', 'WORKFLOW_CONFIG', 'PRODUCTION_BOM', 'PRODUCTION_ORDER',
    'PRODUCTION_WORK_CENTER', 'PRODUCTION_ROUTING'
)
  AND deleted_flag = 0;

-- 库存查询页直接承载预占查看与巡检能力，ERP_ADMIN 必须同时具备对应权限。
INSERT INTO sys_role_menu
(id, role_id, menu_id, created_by)
VALUES
    (7433, 3002, 5016, 0),
    (7434, 3002, 5017, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
