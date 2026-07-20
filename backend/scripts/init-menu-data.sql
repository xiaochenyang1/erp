-- ============================================
-- ERP系统菜单配置SQL脚本
-- 生成日期: 2026-06-16
-- 用途: 初始化系统菜单结构
-- ============================================

-- 注意事项：
-- 1. 执行前请备份数据库
-- 2. 根据实际表结构调整字段名
-- 3. ID可能需要根据实际情况调整
-- 4. 权限码(permission)需要与后端PermissionCodes保持一致

-- ============================================
-- 清理旧数据（可选，首次执行可注释掉）
-- ============================================
-- DELETE FROM sys_menu WHERE id >= 1000;

-- ============================================
-- 一级菜单
-- ============================================

-- 1. 首页
INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (1, NULL, '首页', '/dashboard', 'dashboard/index', 'House', 1, 'MENU', 'dashboard:view', 'ACTIVE', NOW(), NOW());

-- 2. 主数据管理
INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (2, NULL, '主数据管理', '/masterdata', NULL, 'Menu', 2, 'MENU', NULL, 'ACTIVE', NOW(), NOW());

-- 3. 采购管理
INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (3, NULL, '采购管理', '/purchase', NULL, 'ShoppingCart', 3, 'MENU', NULL, 'ACTIVE', NOW(), NOW());

-- 4. 销售管理
INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (4, NULL, '销售管理', '/sales', NULL, 'Sell', 4, 'MENU', NULL, 'ACTIVE', NOW(), NOW());

-- 5. 库存管理
INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (5, NULL, '库存管理', '/inventory', NULL, 'Box', 5, 'MENU', NULL, 'ACTIVE', NOW(), NOW());

-- 6. 财务管理
INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (6, NULL, '财务管理', '/finance', NULL, 'Money', 6, 'MENU', NULL, 'ACTIVE', NOW(), NOW());

-- 7. 生产管理
INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (7, NULL, '生产管理', '/production', NULL, 'Box', 7, 'MENU', NULL, 'ACTIVE', NOW(), NOW());

-- 8. 系统管理
INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (8, NULL, '系统管理', '/system', NULL, 'Setting', 8, 'MENU', NULL, 'ACTIVE', NOW(), NOW());

-- 9. 报表中心
INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (9, NULL, '报表中心', '/reports', 'reports/index', 'DataAnalysis', 9, 'MENU', 'report:view', 'ACTIVE', NOW(), NOW());

-- ============================================
-- 主数据管理 - 二级菜单
-- ============================================

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (201, 2, '产品管理', '/masterdata/products', 'masterdata/products/index', 'Box', 1, 'MENU', 'product:view', 'ACTIVE', NOW(), NOW());

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (202, 2, '客户管理', '/masterdata/customers', 'masterdata/customers/index', 'User', 2, 'MENU', 'customer:view', 'ACTIVE', NOW(), NOW());

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (203, 2, '供应商管理', '/masterdata/suppliers', 'masterdata/suppliers/index', 'OfficeBuilding', 3, 'MENU', 'supplier:view', 'ACTIVE', NOW(), NOW());

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (204, 2, '仓库管理', '/masterdata/warehouses', 'masterdata/warehouses/index', 'House', 4, 'MENU', 'warehouse:view', 'ACTIVE', NOW(), NOW());

-- ============================================
-- 采购管理 - 二级菜单
-- ============================================

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (301, 3, '采购订单', '/purchase/orders', 'purchase/orders/index', 'Document', 1, 'MENU', 'purchase:order:view', 'ACTIVE', NOW(), NOW());

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (302, 3, '采购收货', '/purchase/receipts', 'purchase/receipts/index', 'Box', 2, 'MENU', 'purchase:receipt:view', 'ACTIVE', NOW(), NOW());

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (303, 3, '采购退货', '/purchase/returns', 'purchase/returns/index', 'RefreshLeft', 3, 'MENU', 'purchase:return:view', 'ACTIVE', NOW(), NOW());

-- ============================================
-- 销售管理 - 二级菜单
-- ============================================

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (401, 4, '销售订单', '/sales/orders', 'sales/orders/index', 'Document', 1, 'MENU', 'sales:order:view', 'ACTIVE', NOW(), NOW());

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (402, 4, '销售发货', '/sales/deliveries', 'sales/deliveries/index', 'Box', 2, 'MENU', 'sales:delivery:view', 'ACTIVE', NOW(), NOW());

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (403, 4, '销售退货', '/sales/returns', 'sales/returns/index', 'RefreshLeft', 3, 'MENU', 'sales:return:view', 'ACTIVE', NOW(), NOW());

-- ============================================
-- 库存管理 - 二级菜单
-- ============================================

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (501, 5, '库存查询', '/inventory/stocks', 'inventory/stocks/index', 'List', 1, 'MENU', 'inventory:stock:view', 'ACTIVE', NOW(), NOW());

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (502, 5, '库存调整', '/inventory/adjustments', 'inventory/adjustments/index', 'EditPen', 2, 'MENU', 'inventory:adjust:view', 'ACTIVE', NOW(), NOW());

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (503, 5, '库存盘点', '/inventory/checks', 'inventory/checks/index', 'Document', 3, 'MENU', 'inventory:check:view', 'ACTIVE', NOW(), NOW());

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (504, 5, '库存调拨', '/inventory/transfers', 'inventory/transfers/index', 'Switch', 4, 'MENU', 'inventory:transfer:view', 'ACTIVE', NOW(), NOW());

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (505, 5, '库存预警', '/inventory/alerts', 'inventory/alerts/index', 'Warning', 5, 'MENU', 'inventory:alert:view', 'ACTIVE', NOW(), NOW());

-- ============================================
-- 财务管理 - 二级菜单
-- ============================================

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (601, 6, '会计科目', '/finance/subjects', 'finance/subjects/index', 'Notebook', 1, 'MENU', 'finance:subject:view', 'ACTIVE', NOW(), NOW());

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (602, 6, '凭证管理', '/finance/vouchers', 'finance/vouchers/index', 'Tickets', 2, 'MENU', 'finance:voucher:view', 'ACTIVE', NOW(), NOW());

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (603, 6, '总账查询', '/finance/ledger', 'finance/ledger/index', 'DataLine', 3, 'MENU', 'finance:ledger:view', 'ACTIVE', NOW(), NOW());

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (604, 6, '应收账款', '/finance/receivables', 'finance/receivables/index', 'Document', 4, 'MENU', 'finance:receivable:view', 'ACTIVE', NOW(), NOW());

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (605, 6, '应付账款', '/finance/payables', 'finance/payables/index', 'Document', 5, 'MENU', 'finance:payable:view', 'ACTIVE', NOW(), NOW());

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (606, 6, '收付款管理', '/finance/payments', 'finance/payments/index', 'Money', 6, 'MENU', 'finance:payment:view', 'ACTIVE', NOW(), NOW());

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (607, 6, '费用管理', '/finance/expenses', 'finance/expenses/index', 'Wallet', 7, 'MENU', 'finance:expense:view', 'ACTIVE', NOW(), NOW());

-- ============================================
-- 生产管理 - 二级菜单
-- ============================================

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (701, 7, 'BOM管理', '/production/boms', 'production/boms/index', 'List', 1, 'MENU', 'production:bom:view', 'ACTIVE', NOW(), NOW());

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (702, 7, '生产订单', '/production/orders', 'production/orders/index', 'Document', 2, 'MENU', 'production:order:view', 'ACTIVE', NOW(), NOW());

-- ============================================
-- 系统管理 - 二级菜单
-- ============================================

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (801, 8, '用户管理', '/system/users', 'system/users/index', 'User', 1, 'MENU', 'system:user:view', 'ACTIVE', NOW(), NOW());

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (802, 8, '角色管理', '/system/roles', 'system/roles/index', 'UserFilled', 2, 'MENU', 'system:role:view', 'ACTIVE', NOW(), NOW());

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (803, 8, '菜单管理', '/system/menus', 'system/menus/index', 'Menu', 3, 'MENU', 'system:menu:view', 'ACTIVE', NOW(), NOW());

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (804, 8, '部门管理', '/system/depts', 'system/depts/index', 'OfficeBuilding', 4, 'MENU', 'system:dept:view', 'ACTIVE', NOW(), NOW());

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (805, 8, '岗位管理', '/system/posts', 'system/posts/index', 'Briefcase', 5, 'MENU', 'system:post:view', 'ACTIVE', NOW(), NOW());

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (806, 8, '字典管理', '/system/dicts', 'system/dicts/index', 'Collection', 6, 'MENU', 'system:dict:view', 'ACTIVE', NOW(), NOW());

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (807, 8, '系统配置', '/system/configs', 'system/configs/index', 'Tools', 7, 'MENU', 'system:config:view', 'ACTIVE', NOW(), NOW());

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, order_num, menu_type, permission, status, created_at, updated_at)
VALUES (808, 8, '操作日志', '/system/logs', 'system/logs/index', 'Document', 8, 'MENU', 'system:log:view', 'ACTIVE', NOW(), NOW());

-- ============================================
-- 提交
-- ============================================
COMMIT;

-- ============================================
-- 验证查询
-- ============================================

-- 查看所有一级菜单
SELECT id, name, path, icon, order_num, status
FROM sys_menu
WHERE parent_id IS NULL
ORDER BY order_num;

-- 查看菜单总数
SELECT COUNT(*) as total_menus FROM sys_menu;

-- 查看各模块菜单数
SELECT
    CASE
        WHEN parent_id IS NULL THEN name
        ELSE (SELECT name FROM sys_menu WHERE id = m.parent_id)
    END as module,
    COUNT(*) as menu_count
FROM sys_menu m
GROUP BY module
ORDER BY module;
