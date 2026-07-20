-- V95: 第二批按钮级权限收口配套菜单种子。
-- 为 masterdata(products/customers/suppliers/warehouses)、purchase/orders、
-- inventory/checks、system(depts/dicts/posts/menus/configs) 补 BUTTON 权限节点，
-- permission 与后端 @PreAuthorize 及前端 v-permission 三方对齐，并绑定给 ERP_ADMIN(3002)。
-- SUPER_ADMIN(3001) 走全树，无需绑定。
-- 号段：menu id 5230+（V94 用至 5217），role_menu id 7240+（V94 用至 7229），ON DUPLICATE KEY UPDATE 幂等。
-- 父 MENU：products=5131 customers=5132 suppliers=5133 warehouses=5134
--          purchase/orders=5136 inventory/checks=5145
--          system menus=5004 depts=5005 posts=5006 dicts=5007 configs=5148

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    -- 产品管理
    (5230, 5131, 'BUTTON', 'MASTERDATA_PRODUCT_CREATE', '创建产品', NULL, NULL, 'masterdata:product:create', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5231, 5131, 'BUTTON', 'MASTERDATA_PRODUCT_UPDATE', '修改产品', NULL, NULL, 'masterdata:product:update', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5232, 5131, 'BUTTON', 'MASTERDATA_PRODUCT_ENABLE', '启用产品', NULL, NULL, 'masterdata:product:enable', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5233, 5131, 'BUTTON', 'MASTERDATA_PRODUCT_DISABLE', '停用产品', NULL, NULL, 'masterdata:product:disable', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    -- 客户管理
    (5234, 5132, 'BUTTON', 'MASTERDATA_CUSTOMER_CREATE', '创建客户', NULL, NULL, 'masterdata:customer:create', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5235, 5132, 'BUTTON', 'MASTERDATA_CUSTOMER_UPDATE', '修改客户', NULL, NULL, 'masterdata:customer:update', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5236, 5132, 'BUTTON', 'MASTERDATA_CUSTOMER_ENABLE', '启用客户', NULL, NULL, 'masterdata:customer:enable', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5237, 5132, 'BUTTON', 'MASTERDATA_CUSTOMER_DISABLE', '停用客户', NULL, NULL, 'masterdata:customer:disable', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    -- 供应商管理
    (5238, 5133, 'BUTTON', 'MASTERDATA_SUPPLIER_CREATE', '创建供应商', NULL, NULL, 'masterdata:supplier:create', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5239, 5133, 'BUTTON', 'MASTERDATA_SUPPLIER_UPDATE', '修改供应商', NULL, NULL, 'masterdata:supplier:update', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5240, 5133, 'BUTTON', 'MASTERDATA_SUPPLIER_ENABLE', '启用供应商', NULL, NULL, 'masterdata:supplier:enable', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5241, 5133, 'BUTTON', 'MASTERDATA_SUPPLIER_DISABLE', '停用供应商', NULL, NULL, 'masterdata:supplier:disable', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    -- 仓库管理
    (5242, 5134, 'BUTTON', 'MASTERDATA_WAREHOUSE_CREATE', '创建仓库', NULL, NULL, 'masterdata:warehouse:create', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5243, 5134, 'BUTTON', 'MASTERDATA_WAREHOUSE_UPDATE', '修改仓库', NULL, NULL, 'masterdata:warehouse:update', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5244, 5134, 'BUTTON', 'MASTERDATA_WAREHOUSE_ENABLE', '启用仓库', NULL, NULL, 'masterdata:warehouse:enable', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5245, 5134, 'BUTTON', 'MASTERDATA_WAREHOUSE_DISABLE', '停用仓库', NULL, NULL, 'masterdata:warehouse:disable', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    -- 采购订单
    (5250, 5136, 'BUTTON', 'PURCHASE_ORDER_CREATE', '创建采购订单', NULL, NULL, 'purchase:order:create', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5251, 5136, 'BUTTON', 'PURCHASE_ORDER_UPDATE', '修改采购订单', NULL, NULL, 'purchase:order:update', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5252, 5136, 'BUTTON', 'PURCHASE_ORDER_SUBMIT', '提交采购订单', NULL, NULL, 'purchase:order:submit', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5253, 5136, 'BUTTON', 'PURCHASE_ORDER_APPROVE', '审批采购订单', NULL, NULL, 'purchase:order:approve', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    (5254, 5136, 'BUTTON', 'PURCHASE_ORDER_REJECT', '驳回采购订单', NULL, NULL, 'purchase:order:reject', 5, 1, 'ACTIVE', 0, 0, 0, 0),
    (5255, 5136, 'BUTTON', 'PURCHASE_ORDER_CANCEL', '取消采购订单', NULL, NULL, 'purchase:order:cancel', 6, 1, 'ACTIVE', 0, 0, 0, 0),
    (5256, 5136, 'BUTTON', 'PURCHASE_ORDER_CLOSE', '关闭采购订单', NULL, NULL, 'purchase:order:close', 7, 1, 'ACTIVE', 0, 0, 0, 0),
    -- 库存盘点
    (5258, 5145, 'BUTTON', 'INVENTORY_CHECK_CREATE', '创建库存盘点', NULL, NULL, 'inventory:check:create', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5259, 5145, 'BUTTON', 'INVENTORY_CHECK_ADJUST', '盘点差异调整', NULL, NULL, 'inventory:check:adjust', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5260, 5145, 'BUTTON', 'INVENTORY_CHECK_CANCEL', '取消库存盘点', NULL, NULL, 'inventory:check:cancel', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    -- 菜单管理
    (5261, 5004, 'BUTTON', 'SYSTEM_MENU_CREATE', '创建菜单', NULL, NULL, 'system:menu:create', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5262, 5004, 'BUTTON', 'SYSTEM_MENU_UPDATE', '修改菜单', NULL, NULL, 'system:menu:update', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5263, 5004, 'BUTTON', 'SYSTEM_MENU_ENABLE', '启用菜单', NULL, NULL, 'system:menu:enable', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5264, 5004, 'BUTTON', 'SYSTEM_MENU_DISABLE', '停用菜单', NULL, NULL, 'system:menu:disable', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    -- 部门管理
    (5265, 5005, 'BUTTON', 'SYSTEM_DEPT_CREATE', '创建部门', NULL, NULL, 'system:dept:create', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5266, 5005, 'BUTTON', 'SYSTEM_DEPT_UPDATE', '修改部门', NULL, NULL, 'system:dept:update', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5267, 5005, 'BUTTON', 'SYSTEM_DEPT_ENABLE', '启用部门', NULL, NULL, 'system:dept:enable', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5268, 5005, 'BUTTON', 'SYSTEM_DEPT_DISABLE', '停用部门', NULL, NULL, 'system:dept:disable', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    -- 岗位管理
    (5269, 5006, 'BUTTON', 'SYSTEM_POST_CREATE', '创建岗位', NULL, NULL, 'system:post:create', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5270, 5006, 'BUTTON', 'SYSTEM_POST_UPDATE', '修改岗位', NULL, NULL, 'system:post:update', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5271, 5006, 'BUTTON', 'SYSTEM_POST_ENABLE', '启用岗位', NULL, NULL, 'system:post:enable', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5272, 5006, 'BUTTON', 'SYSTEM_POST_DISABLE', '停用岗位', NULL, NULL, 'system:post:disable', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    -- 字典管理
    (5273, 5007, 'BUTTON', 'SYSTEM_DICT_CREATE', '创建字典', NULL, NULL, 'system:dict:create', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5274, 5007, 'BUTTON', 'SYSTEM_DICT_UPDATE', '修改字典', NULL, NULL, 'system:dict:update', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5275, 5007, 'BUTTON', 'SYSTEM_DICT_ENABLE', '启用字典', NULL, NULL, 'system:dict:enable', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5276, 5007, 'BUTTON', 'SYSTEM_DICT_DISABLE', '停用字典', NULL, NULL, 'system:dict:disable', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    -- 系统配置（含流水号规则，同一前端页 /system/configs）
    (5277, 5148, 'BUTTON', 'SYSTEM_CONFIG_CREATE', '创建配置', NULL, NULL, 'system:config:create', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5278, 5148, 'BUTTON', 'SYSTEM_CONFIG_UPDATE', '修改配置', NULL, NULL, 'system:config:update', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5279, 5148, 'BUTTON', 'SYSTEM_CONFIG_ENABLE', '启用配置', NULL, NULL, 'system:config:enable', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5280, 5148, 'BUTTON', 'SYSTEM_CONFIG_DISABLE', '停用配置', NULL, NULL, 'system:config:disable', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    (5281, 5148, 'BUTTON', 'SYSTEM_SEQUENCE_RULE_CREATE', '创建流水号规则', NULL, NULL, 'system:sequence-rule:create', 5, 1, 'ACTIVE', 0, 0, 0, 0),
    (5282, 5148, 'BUTTON', 'SYSTEM_SEQUENCE_RULE_UPDATE', '修改流水号规则', NULL, NULL, 'system:sequence-rule:update', 6, 1, 'ACTIVE', 0, 0, 0, 0),
    (5283, 5148, 'BUTTON', 'SYSTEM_SEQUENCE_RULE_ENABLE', '启用流水号规则', NULL, NULL, 'system:sequence-rule:enable', 7, 1, 'ACTIVE', 0, 0, 0, 0),
    (5284, 5148, 'BUTTON', 'SYSTEM_SEQUENCE_RULE_DISABLE', '停用流水号规则', NULL, NULL, 'system:sequence-rule:disable', 8, 1, 'ACTIVE', 0, 0, 0, 0)
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

-- 绑定给 ERP_ADMIN(3002)
INSERT INTO sys_role_menu
(id, role_id, menu_id, created_by)
VALUES
    (7240, 3002, 5230, 0), (7241, 3002, 5231, 0), (7242, 3002, 5232, 0), (7243, 3002, 5233, 0),
    (7244, 3002, 5234, 0), (7245, 3002, 5235, 0), (7246, 3002, 5236, 0), (7247, 3002, 5237, 0),
    (7248, 3002, 5238, 0), (7249, 3002, 5239, 0), (7250, 3002, 5240, 0), (7251, 3002, 5241, 0),
    (7252, 3002, 5242, 0), (7253, 3002, 5243, 0), (7254, 3002, 5244, 0), (7255, 3002, 5245, 0),
    (7256, 3002, 5250, 0), (7257, 3002, 5251, 0), (7258, 3002, 5252, 0), (7259, 3002, 5253, 0),
    (7260, 3002, 5254, 0), (7261, 3002, 5255, 0), (7262, 3002, 5256, 0),
    (7263, 3002, 5258, 0), (7264, 3002, 5259, 0), (7265, 3002, 5260, 0),
    (7266, 3002, 5261, 0), (7267, 3002, 5262, 0), (7268, 3002, 5263, 0), (7269, 3002, 5264, 0),
    (7270, 3002, 5265, 0), (7271, 3002, 5266, 0), (7272, 3002, 5267, 0), (7273, 3002, 5268, 0),
    (7274, 3002, 5269, 0), (7275, 3002, 5270, 0), (7276, 3002, 5271, 0), (7277, 3002, 5272, 0),
    (7278, 3002, 5273, 0), (7279, 3002, 5274, 0), (7280, 3002, 5275, 0), (7281, 3002, 5276, 0),
    (7282, 3002, 5277, 0), (7283, 3002, 5278, 0), (7284, 3002, 5279, 0), (7285, 3002, 5280, 0),
    (7286, 3002, 5281, 0), (7287, 3002, 5282, 0), (7288, 3002, 5283, 0), (7289, 3002, 5284, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
