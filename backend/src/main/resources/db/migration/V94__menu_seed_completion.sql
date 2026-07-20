-- V94: 补齐前端已有但后端菜单表缺失的 CATALOG/MENU 节点，并补首批高风险写操作页的 BUTTON 权限节点。
-- 背景：runtime-menu-tree 已上线，SUPER_ADMIN 走全树、ERP_ADMIN(3002) 走 role_menu。
-- 缺 MENU 节点的页面在 admin 侧边栏也会消失，故此处补齐全部缺失 MENU；BUTTON 供 ERP_ADMIN/自定义角色授权。
-- path/component 一律对齐 erp-frontend 真实路由，避免动态菜单匹配不到。
-- 号段：menu id 5130+（已占用至 5120），role_menu id 7170+（已占用至 7164），均 ON DUPLICATE KEY UPDATE 幂等。

-- 1) 校正历史 path 不一致：会计科目前端路由为 /finance/subjects
UPDATE sys_menu
SET path = '/finance/subjects',
    component = 'finance/subjects/index',
    updated_by = 0
WHERE menu_code = 'FINANCE_SUBJECT'
  AND deleted_flag = 0;

-- 2) 新增 CATALOG + MENU 节点（覆盖前端全部缺失路由）
INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    -- 主数据 CATALOG + 4 页
    (5130, 0, 'CATALOG', 'MASTERDATA', '主数据', '/masterdata', 'Layout', NULL, 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5131, 5130, 'MENU', 'MASTERDATA_PRODUCT', '产品管理', '/masterdata/products', 'masterdata/products/index', 'masterdata:product:view', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5132, 5130, 'MENU', 'MASTERDATA_CUSTOMER', '客户管理', '/masterdata/customers', 'masterdata/customers/index', 'masterdata:customer:view', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5133, 5130, 'MENU', 'MASTERDATA_SUPPLIER', '供应商管理', '/masterdata/suppliers', 'masterdata/suppliers/index', 'masterdata:supplier:view', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5134, 5130, 'MENU', 'MASTERDATA_WAREHOUSE', '仓库管理', '/masterdata/warehouses', 'masterdata/warehouses/index', 'masterdata:warehouse:view', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    -- 采购 CATALOG + 3 页
    (5135, 0, 'CATALOG', 'PURCHASE', '采购管理', '/purchase', 'Layout', NULL, 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5136, 5135, 'MENU', 'PURCHASE_ORDER', '采购订单', '/purchase/orders', 'purchase/orders/index', 'purchase:order:view', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5137, 5135, 'MENU', 'PURCHASE_RECEIPT', '采购收货', '/purchase/receipts', 'purchase/receipts/index', 'purchase:receipt:view', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5138, 5135, 'MENU', 'PURCHASE_RETURN', '采购退货', '/purchase/returns', 'purchase/returns/index', 'purchase:return:view', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    -- 销售 CATALOG + 3 页
    (5139, 0, 'CATALOG', 'SALES', '销售管理', '/sales', 'Layout', NULL, 4, 1, 'ACTIVE', 0, 0, 0, 0),
    (5140, 5139, 'MENU', 'SALES_ORDER', '销售订单', '/sales/orders', 'sales/orders/index', 'sales:order:view', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5141, 5139, 'MENU', 'SALES_DELIVERY', '销售发货', '/sales/deliveries', 'sales/deliveries/index', 'sales:delivery:view', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5142, 5139, 'MENU', 'SALES_RETURN', '销售退货', '/sales/returns', 'sales/returns/index', 'sales:return:view', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    -- 库存补 4 页（挂现有 CATALOG 5009）
    (5143, 5009, 'MENU', 'INVENTORY_STOCK', '库存查询', '/inventory/stocks', 'inventory/stocks/index', 'inventory:stock:view', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5144, 5009, 'MENU', 'INVENTORY_ADJUSTMENT', '库存调整', '/inventory/adjustments', 'inventory/adjustments/index', 'inventory:adjustment:view', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5145, 5009, 'MENU', 'INVENTORY_CHECK', '库存盘点', '/inventory/checks', 'inventory/checks/index', 'inventory:check:view', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5146, 5009, 'MENU', 'INVENTORY_ALERT', '库存预警', '/inventory/alerts', 'inventory/alerts/index', 'inventory:alert:view', 5, 1, 'ACTIVE', 0, 0, 0, 0),
    -- 财务补收付款（挂现有 CATALOG 5030）
    (5147, 5030, 'MENU', 'FINANCE_PAYMENT', '收付款管理', '/finance/payments', 'finance/payments/index', 'finance:payment:view', 8, 1, 'ACTIVE', 0, 0, 0, 0),
    -- 系统补配置、导入任务（挂现有 CATALOG 5001）
    (5148, 5001, 'MENU', 'SYSTEM_CONFIG', '系统配置', '/system/configs', 'system/configs/index', 'system:config:view', 12, 1, 'ACTIVE', 0, 0, 0, 0),
    (5149, 5001, 'MENU', 'SYSTEM_IMPORT', '导入任务', '/system/imports', 'system/imports/index', 'import:init:manage', 13, 1, 'ACTIVE', 0, 0, 0, 0),
    -- 报表中心首页 + 单据追踪（挂现有 CATALOG 5020）
    (5150, 5020, 'MENU', 'REPORT_CENTER', '报表中心', '/reports', 'reports/index', 'report:view', 0, 1, 'ACTIVE', 0, 0, 0, 0),
    (5151, 5020, 'MENU', 'REPORT_BUSINESS_TRACE', '单据追踪', '/reports/traces', 'reports/traces/index', 'report:view', 6, 1, 'ACTIVE', 0, 0, 0, 0),

    -- 3) 首批高风险写操作页 BUTTON 节点
    -- 销售订单
    (5180, 5140, 'BUTTON', 'SALES_ORDER_CREATE', '创建销售订单', NULL, NULL, 'sales:order:create', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5181, 5140, 'BUTTON', 'SALES_ORDER_UPDATE', '修改销售订单', NULL, NULL, 'sales:order:update', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5182, 5140, 'BUTTON', 'SALES_ORDER_SUBMIT', '提交销售订单', NULL, NULL, 'sales:order:submit', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5183, 5140, 'BUTTON', 'SALES_ORDER_APPROVE', '审批销售订单', NULL, NULL, 'sales:order:approve', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    (5184, 5140, 'BUTTON', 'SALES_ORDER_UNAPPROVE', '反审核销售订单', NULL, NULL, 'sales:order:unapprove', 5, 1, 'ACTIVE', 0, 0, 0, 0),
    (5185, 5140, 'BUTTON', 'SALES_ORDER_REJECT', '驳回销售订单', NULL, NULL, 'sales:order:reject', 6, 1, 'ACTIVE', 0, 0, 0, 0),
    (5186, 5140, 'BUTTON', 'SALES_ORDER_CANCEL', '取消销售订单', NULL, NULL, 'sales:order:cancel', 7, 1, 'ACTIVE', 0, 0, 0, 0),
    -- 销售发货
    (5187, 5141, 'BUTTON', 'SALES_DELIVERY_CREATE', '创建销售发货', NULL, NULL, 'sales:delivery:create', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5188, 5141, 'BUTTON', 'SALES_DELIVERY_UPDATE', '修改销售发货', NULL, NULL, 'sales:delivery:update', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5189, 5141, 'BUTTON', 'SALES_DELIVERY_CANCEL', '取消销售发货', NULL, NULL, 'sales:delivery:cancel', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5190, 5141, 'BUTTON', 'SALES_DELIVERY_POST', '过账销售发货', NULL, NULL, 'sales:delivery:post', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    -- 销售退货
    (5191, 5142, 'BUTTON', 'SALES_RETURN_CREATE', '创建销售退货', NULL, NULL, 'sales:return:create', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5192, 5142, 'BUTTON', 'SALES_RETURN_UPDATE', '修改销售退货', NULL, NULL, 'sales:return:update', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5193, 5142, 'BUTTON', 'SALES_RETURN_CANCEL', '取消销售退货', NULL, NULL, 'sales:return:cancel', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5194, 5142, 'BUTTON', 'SALES_RETURN_POST', '过账销售退货', NULL, NULL, 'sales:return:post', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    -- 采购收货
    (5195, 5137, 'BUTTON', 'PURCHASE_RECEIPT_CREATE', '创建采购收货', NULL, NULL, 'purchase:receipt:create', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5196, 5137, 'BUTTON', 'PURCHASE_RECEIPT_UPDATE', '修改采购收货', NULL, NULL, 'purchase:receipt:update', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5197, 5137, 'BUTTON', 'PURCHASE_RECEIPT_CANCEL', '取消采购收货', NULL, NULL, 'purchase:receipt:cancel', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5198, 5137, 'BUTTON', 'PURCHASE_RECEIPT_POST', '过账采购收货', NULL, NULL, 'purchase:receipt:post', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    -- 采购退货
    (5199, 5138, 'BUTTON', 'PURCHASE_RETURN_CREATE', '创建采购退货', NULL, NULL, 'purchase:return:create', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5200, 5138, 'BUTTON', 'PURCHASE_RETURN_UPDATE', '修改采购退货', NULL, NULL, 'purchase:return:update', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5201, 5138, 'BUTTON', 'PURCHASE_RETURN_CANCEL', '取消采购退货', NULL, NULL, 'purchase:return:cancel', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5202, 5138, 'BUTTON', 'PURCHASE_RETURN_POST', '过账采购退货', NULL, NULL, 'purchase:return:post', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    -- 库存调整
    (5203, 5144, 'BUTTON', 'INVENTORY_ADJUSTMENT_CREATE', '创建库存调整', NULL, NULL, 'inventory:adjustment:create', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5204, 5144, 'BUTTON', 'INVENTORY_ADJUSTMENT_POST', '过账库存调整', NULL, NULL, 'inventory:adjustment:post', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5205, 5144, 'BUTTON', 'INVENTORY_ADJUSTMENT_CANCEL', '取消库存调整', NULL, NULL, 'inventory:adjustment:cancel', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    -- 库存调拨（补 cancel，create/post 已在 5014/5015）
    (5206, 5013, 'BUTTON', 'INVENTORY_TRANSFER_CANCEL', '取消库存调拨', NULL, NULL, 'inventory:transfer:cancel', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    -- 生产工单（补 return-materials，其余已在 5084-5089/5094）
    (5207, 5083, 'BUTTON', 'PRODUCTION_ORDER_RETURN', '生产退料', NULL, NULL, 'production:order:return', 7, 1, 'ACTIVE', 0, 0, 0, 0),
    -- 用户管理（reset-password 已在 5026）
    (5208, 5002, 'BUTTON', 'SYSTEM_USER_CREATE', '创建用户', NULL, NULL, 'system:user:create', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5209, 5002, 'BUTTON', 'SYSTEM_USER_UPDATE', '修改用户', NULL, NULL, 'system:user:update', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5210, 5002, 'BUTTON', 'SYSTEM_USER_ENABLE', '启用用户', NULL, NULL, 'system:user:enable', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5211, 5002, 'BUTTON', 'SYSTEM_USER_DISABLE', '停用用户', NULL, NULL, 'system:user:disable', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    (5212, 5002, 'BUTTON', 'SYSTEM_USER_ASSIGN_ROLE', '分配用户角色', NULL, NULL, 'system:user:assign-role', 5, 1, 'ACTIVE', 0, 0, 0, 0),
    -- 角色管理
    (5213, 5003, 'BUTTON', 'SYSTEM_ROLE_CREATE', '创建角色', NULL, NULL, 'system:role:create', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5214, 5003, 'BUTTON', 'SYSTEM_ROLE_UPDATE', '修改角色', NULL, NULL, 'system:role:update', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5215, 5003, 'BUTTON', 'SYSTEM_ROLE_ENABLE', '启用角色', NULL, NULL, 'system:role:enable', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5216, 5003, 'BUTTON', 'SYSTEM_ROLE_DISABLE', '停用角色', NULL, NULL, 'system:role:disable', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    (5217, 5003, 'BUTTON', 'SYSTEM_ROLE_ASSIGN_MENU', '分配角色菜单', NULL, NULL, 'system:role:assign-menu', 5, 1, 'ACTIVE', 0, 0, 0, 0)
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

-- 4) 绑定给 ERP_ADMIN(3002)；SUPER_ADMIN(3001) 走全树不需绑定
INSERT INTO sys_role_menu
(id, role_id, menu_id, created_by)
VALUES
    (7170, 3002, 5130, 0), (7171, 3002, 5131, 0), (7172, 3002, 5132, 0), (7173, 3002, 5133, 0),
    (7174, 3002, 5134, 0), (7175, 3002, 5135, 0), (7176, 3002, 5136, 0), (7177, 3002, 5137, 0),
    (7178, 3002, 5138, 0), (7179, 3002, 5139, 0), (7180, 3002, 5140, 0), (7181, 3002, 5141, 0),
    (7182, 3002, 5142, 0), (7183, 3002, 5143, 0), (7184, 3002, 5144, 0), (7185, 3002, 5145, 0),
    (7186, 3002, 5146, 0), (7187, 3002, 5147, 0), (7188, 3002, 5148, 0), (7189, 3002, 5149, 0),
    (7190, 3002, 5150, 0), (7191, 3002, 5151, 0),
    (7192, 3002, 5180, 0), (7193, 3002, 5181, 0), (7194, 3002, 5182, 0), (7195, 3002, 5183, 0),
    (7196, 3002, 5184, 0), (7197, 3002, 5185, 0), (7198, 3002, 5186, 0), (7199, 3002, 5187, 0),
    (7200, 3002, 5188, 0), (7201, 3002, 5189, 0), (7202, 3002, 5190, 0), (7203, 3002, 5191, 0),
    (7204, 3002, 5192, 0), (7205, 3002, 5193, 0), (7206, 3002, 5194, 0), (7207, 3002, 5195, 0),
    (7208, 3002, 5196, 0), (7209, 3002, 5197, 0), (7210, 3002, 5198, 0), (7211, 3002, 5199, 0),
    (7212, 3002, 5200, 0), (7213, 3002, 5201, 0), (7214, 3002, 5202, 0), (7215, 3002, 5203, 0),
    (7216, 3002, 5204, 0), (7217, 3002, 5205, 0), (7218, 3002, 5206, 0), (7219, 3002, 5207, 0),
    (7220, 3002, 5208, 0), (7221, 3002, 5209, 0), (7222, 3002, 5210, 0), (7223, 3002, 5211, 0),
    (7224, 3002, 5212, 0), (7225, 3002, 5213, 0), (7226, 3002, 5214, 0), (7227, 3002, 5215, 0),
    (7228, 3002, 5216, 0), (7229, 3002, 5217, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
