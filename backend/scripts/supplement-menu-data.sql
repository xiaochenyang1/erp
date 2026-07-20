-- ============================================
-- ERP系统菜单补充配置SQL脚本
-- 生成日期: 2026-06-16
-- 用途: 补充缺失的采购和销售模块菜单
-- ============================================

-- 注意：根据现有数据，使用正确的字段名和结构

-- ============================================
-- 采购管理模块
-- ============================================

-- 一级菜单：采购管理
INSERT INTO sys_menu (
    id, parent_id, menu_type, menu_code, menu_name, path, component,
    permission, sort_no, visible_flag, status, deleted_flag,
    created_by, created_time, updated_by, updated_time, version
) VALUES (
    5050, 0, 'MENU', 'PURCHASE', '采购管理', '/purchase', NULL,
    NULL, 30, 1, 'ACTIVE', 0,
    0, NOW(), 0, NOW(), 0
);

-- 采购订单
INSERT INTO sys_menu (
    id, parent_id, menu_type, menu_code, menu_name, path, component,
    permission, sort_no, visible_flag, status, deleted_flag,
    created_by, created_time, updated_by, updated_time, version
) VALUES (
    5051, 5050, 'MENU', 'PURCHASE_ORDER', '采购订单', '/purchase/orders', 'purchase/orders/index',
    'purchase:order:view', 10, 1, 'ACTIVE', 0,
    0, NOW(), 0, NOW(), 0
);

-- 采购收货
INSERT INTO sys_menu (
    id, parent_id, menu_type, menu_code, menu_name, path, component,
    permission, sort_no, visible_flag, status, deleted_flag,
    created_by, created_time, updated_by, updated_time, version
) VALUES (
    5052, 5050, 'MENU', 'PURCHASE_RECEIPT', '采购收货', '/purchase/receipts', 'purchase/receipts/index',
    'purchase:receipt:view', 20, 1, 'ACTIVE', 0,
    0, NOW(), 0, NOW(), 0
);

-- 采购退货
INSERT INTO sys_menu (
    id, parent_id, menu_type, menu_code, menu_name, path, component,
    permission, sort_no, visible_flag, status, deleted_flag,
    created_by, created_time, updated_by, updated_time, version
) VALUES (
    5053, 5050, 'MENU', 'PURCHASE_RETURN', '采购退货', '/purchase/returns', 'purchase/returns/index',
    'purchase:return:view', 30, 1, 'ACTIVE', 0,
    0, NOW(), 0, NOW(), 0
);

-- ============================================
-- 销售管理模块
-- ============================================

-- 一级菜单：销售管理
INSERT INTO sys_menu (
    id, parent_id, menu_type, menu_code, menu_name, path, component,
    permission, sort_no, visible_flag, status, deleted_flag,
    created_by, created_time, updated_by, updated_time, version
) VALUES (
    5060, 0, 'MENU', 'SALES', '销售管理', '/sales', NULL,
    NULL, 40, 1, 'ACTIVE', 0,
    0, NOW(), 0, NOW(), 0
);

-- 销售订单
INSERT INTO sys_menu (
    id, parent_id, menu_type, menu_code, menu_name, path, component,
    permission, sort_no, visible_flag, status, deleted_flag,
    created_by, created_time, updated_by, updated_time, version
) VALUES (
    5061, 5060, 'MENU', 'SALES_ORDER', '销售订单', '/sales/orders', 'sales/orders/index',
    'sales:order:view', 10, 1, 'ACTIVE', 0,
    0, NOW(), 0, NOW(), 0
);

-- 销售发货
INSERT INTO sys_menu (
    id, parent_id, menu_type, menu_code, menu_name, path, component,
    permission, sort_no, visible_flag, status, deleted_flag,
    created_by, created_time, updated_by, updated_time, version
) VALUES (
    5062, 5060, 'MENU', 'SALES_DELIVERY', '销售发货', '/sales/deliveries', 'sales/deliveries/index',
    'sales:delivery:view', 20, 1, 'ACTIVE', 0,
    0, NOW(), 0, NOW(), 0
);

-- 销售退货
INSERT INTO sys_menu (
    id, parent_id, menu_type, menu_code, menu_name, path, component,
    permission, sort_no, visible_flag, status, deleted_flag,
    created_by, created_time, updated_by, updated_time, version
) VALUES (
    5063, 5060, 'MENU', 'SALES_RETURN', '销售退货', '/sales/returns', 'sales/returns/index',
    'sales:return:view', 30, 1, 'ACTIVE', 0,
    0, NOW(), 0, NOW(), 0
);

-- ============================================
-- 首页菜单（如果不存在）
-- ============================================

INSERT INTO sys_menu (
    id, parent_id, menu_type, menu_code, menu_name, path, component,
    permission, sort_no, visible_flag, status, deleted_flag,
    created_by, created_time, updated_by, updated_time, version
) VALUES (
    5000, 0, 'MENU', 'DASHBOARD', '首页', '/dashboard', 'dashboard/index',
    'dashboard:view', 1, 1, 'ACTIVE', 0,
    0, NOW(), 0, NOW(), 0
)
ON DUPLICATE KEY UPDATE menu_name = '首页';

-- ============================================
-- 提交
-- ============================================
COMMIT;

-- ============================================
-- 验证查询
-- ============================================

-- 查看所有一级菜单
SELECT id, menu_code, menu_name, path, sort_no
FROM sys_menu
WHERE parent_id = 0
ORDER BY sort_no;

-- 查看菜单总数
SELECT COUNT(*) as total_menus FROM sys_menu WHERE deleted_flag = 0;

-- 查看采购管理菜单
SELECT id, parent_id, menu_code, menu_name, path
FROM sys_menu
WHERE parent_id = 5050 OR id = 5050
ORDER BY id;

-- 查看销售管理菜单
SELECT id, parent_id, menu_code, menu_name, path
FROM sys_menu
WHERE parent_id = 5060 OR id = 5060
ORDER BY id;
