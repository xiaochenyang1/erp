INSERT INTO sys_config
(id, config_code, config_name, config_value, status, deleted_flag, remark, created_by, updated_by, version)
VALUES
    (1002, 'erp.stock.allow-negative', '是否允许负库存', 'false', 'ACTIVE', 0, '库存出库默认不允许负库存', 0, 0, 0),
    (1003, 'erp.approval.enabled', '审批开关', 'true', 'ACTIVE', 0, '业务审批默认启用', 0, 0, 0),
    (1004, 'erp.bootstrap.admin-password-initialized', '生产管理员密码是否已初始化', 'false', 'ACTIVE', 0, '首次生产启动由 ERP_BOOTSTRAP_ADMIN_PASSWORD 写入后置为 true', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    config_name = VALUES(config_name),
    config_value = CASE
        WHEN config_code = 'erp.bootstrap.admin-password-initialized' AND config_value = 'true' THEN config_value
        ELSE VALUES(config_value)
    END,
    status = VALUES(status),
    deleted_flag = VALUES(deleted_flag),
    remark = VALUES(remark);

INSERT INTO sys_sequence_rule
(id, biz_type, prefix, date_pattern, seq_length, current_value, status, created_by, updated_by, version)
VALUES
    (2001, 'PURCHASE_ORDER', 'PO', 'yyyyMMdd', 4, 1, 'ACTIVE', 0, 0, 0),
    (2002, 'SALES_ORDER', 'SO', 'yyyyMMdd', 4, 1, 'ACTIVE', 0, 0, 0),
    (2003, 'INVENTORY_ADJUSTMENT', 'IA', 'yyyyMMdd', 4, 1, 'ACTIVE', 0, 0, 0),
    (2004, 'PURCHASE_RECEIPT', 'PR', 'yyyyMMdd', 4, 1, 'ACTIVE', 0, 0, 0),
    (2005, 'PURCHASE_RETURN', 'PRT', 'yyyyMMdd', 4, 1, 'ACTIVE', 0, 0, 0),
    (2006, 'FIN_PAYMENT', 'FP', 'yyyyMMdd', 4, 1, 'ACTIVE', 0, 0, 0),
    (2007, 'FIN_RECEIPT', 'FR', 'yyyyMMdd', 4, 1, 'ACTIVE', 0, 0, 0),
    (2008, 'INVENTORY_TRANSFER', 'IT', 'yyyyMMdd', 4, 1, 'ACTIVE', 0, 0, 0),
    (2009, 'SALES_DELIVERY', 'SD', 'yyyyMMdd', 4, 1, 'ACTIVE', 0, 0, 0),
    (2010, 'SALES_RETURN', 'SRT', 'yyyyMMdd', 4, 1, 'ACTIVE', 0, 0, 0),
    (2011, 'INVENTORY_STOCK_CHECK', 'IC', 'yyyyMMdd', 4, 1, 'ACTIVE', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    prefix = VALUES(prefix),
    date_pattern = VALUES(date_pattern),
    seq_length = VALUES(seq_length),
    current_value = GREATEST(current_value, VALUES(current_value)),
    status = VALUES(status);

INSERT INTO sys_role
(id, role_code, role_name, status, deleted_flag, remark, created_by, updated_by, version)
VALUES
    (3001, 'SUPER_ADMIN', '超级管理员', 'ACTIVE', 0, '生产内置超级管理员角色', 0, 0, 0),
    (3002, 'ERP_ADMIN', '企业管理员', 'ACTIVE', 0, '生产内置企业管理员角色', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    role_name = VALUES(role_name),
    status = VALUES(status),
    deleted_flag = VALUES(deleted_flag),
    remark = VALUES(remark);

INSERT INTO sys_dept
(id, parent_id, dept_code, dept_name, leader_user_id, sort_no, status, deleted_flag, remark, created_by, updated_by, version)
VALUES
    (3501, 0, 'HEAD_OFFICE', '总部', 4001, 1, 'ACTIVE', 0, '系统默认根部门', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    dept_name = VALUES(dept_name),
    leader_user_id = VALUES(leader_user_id),
    sort_no = VALUES(sort_no),
    status = VALUES(status),
    deleted_flag = VALUES(deleted_flag),
    remark = VALUES(remark);

INSERT INTO sys_post
(id, dept_id, post_code, post_name, status, deleted_flag, remark, created_by, updated_by, version)
VALUES
    (3601, 3501, 'ADMIN_POST', '系统管理员岗位', 'ACTIVE', 0, '系统默认岗位', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    dept_id = VALUES(dept_id),
    post_name = VALUES(post_name),
    status = VALUES(status),
    deleted_flag = VALUES(deleted_flag),
    remark = VALUES(remark);

INSERT INTO sys_user
(id, company_id, account_book_id, username, password, employee_no, real_name, dept_id, post_id, status, deleted_flag, remark, created_by, updated_by, version)
VALUES
    (4001, 1, 1, 'admin', '$2a$10$Ms6YLk8eBEPKgRWKFCr0nOrQTg6cX9mXsJmptcPSFrCZwbjtR6/Gu', 'EMP_ADMIN', '系统管理员', 3501, 3601, 'ACTIVE', 0, '生产首次启动会通过 ERP_BOOTSTRAP_ADMIN_PASSWORD 重置密码', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    employee_no = VALUES(employee_no),
    real_name = VALUES(real_name),
    dept_id = VALUES(dept_id),
    post_id = VALUES(post_id),
    status = VALUES(status),
    deleted_flag = VALUES(deleted_flag),
    remark = VALUES(remark);

INSERT INTO sys_user_role
(id, user_id, role_id, created_by)
VALUES
    (6001, 4001, 3001, 0)
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    role_id = VALUES(role_id);

INSERT INTO md_warehouse
(id, warehouse_code, warehouse_name, dept_id, manager_user_id, address, status, deleted_flag, remark, created_by, updated_by, version)
VALUES
    (4501, 'MAIN_WH', '默认主仓', 3501, 4001, '总部默认仓库地址', 'ACTIVE', 0, '系统初始化默认仓库', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    warehouse_name = VALUES(warehouse_name),
    dept_id = VALUES(dept_id),
    manager_user_id = VALUES(manager_user_id),
    address = VALUES(address),
    status = VALUES(status),
    deleted_flag = VALUES(deleted_flag),
    remark = VALUES(remark);

INSERT INTO sys_role_data_scope
(id, role_id, scope_type, warehouse_id, created_by)
VALUES
    (16001, 3001, 'ALL', NULL, 0),
    (16002, 3002, 'ALL', NULL, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    scope_type = VALUES(scope_type),
    warehouse_id = VALUES(warehouse_id);

INSERT INTO sys_user_data_scope
(id, user_id, scope_type, warehouse_id, created_by)
VALUES
    (16003, 4001, 'ALL', NULL, 0)
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    scope_type = VALUES(scope_type),
    warehouse_id = VALUES(warehouse_id);

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag, status, deleted_flag, created_by, updated_by, version)
VALUES
    (5001, 0, 'CATALOG', 'SYSTEM', '系统管理', '/system', 'Layout', NULL, 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5002, 5001, 'MENU', 'SYSTEM_USER', '用户管理', '/system/users', 'system/user/index', 'system:user:view', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5003, 5001, 'MENU', 'SYSTEM_ROLE', '角色管理', '/system/roles', 'system/role/index', 'system:role:view', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5004, 5001, 'MENU', 'SYSTEM_MENU', '菜单管理', '/system/menus', 'system/menu/index', 'system:menu:view', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5005, 5001, 'MENU', 'SYSTEM_DEPT', '部门管理', '/system/depts', 'system/dept/index', 'system:dept:view', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    (5006, 5001, 'MENU', 'SYSTEM_POST', '岗位管理', '/system/posts', 'system/post/index', 'system:post:view', 5, 1, 'ACTIVE', 0, 0, 0, 0),
    (5007, 5001, 'MENU', 'SYSTEM_DICT', '字典管理', '/system/dicts', 'system/dict/index', 'system:dict:view', 6, 1, 'ACTIVE', 0, 0, 0, 0),
    (5008, 5001, 'MENU', 'SYSTEM_LOG', '日志管理', '/system/logs', 'system/log/index', 'system:log:view', 7, 1, 'ACTIVE', 0, 0, 0, 0),
    (5009, 0, 'CATALOG', 'INVENTORY', '库存管理', '/inventory', 'Layout', NULL, 5, 1, 'ACTIVE', 0, 0, 0, 0),
    (5013, 5009, 'MENU', 'INVENTORY_TRANSFER', '库存调拨', '/inventory/transfers', 'inventory/transfer/index', 'inventory:transfer:view', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    (5014, 5013, 'BUTTON', 'INVENTORY_TRANSFER_CREATE', '创建库存调拨', NULL, NULL, 'inventory:transfer:create', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5015, 5013, 'BUTTON', 'INVENTORY_TRANSFER_POST', '过账库存调拨', NULL, NULL, 'inventory:transfer:post', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5010, 0, 'CATALOG', 'WORKFLOW', '审批中心', '/workflow', 'Layout', NULL, 6, 1, 'ACTIVE', 0, 0, 0, 0),
    (5011, 5010, 'MENU', 'WORKFLOW_TASK', '审批待办', '/workflow/tasks', 'workflow/task/index', 'workflow:view', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5012, 5010, 'MENU', 'WORKFLOW_RECORD', '审批记录', '/workflow/records', 'workflow/record/index', 'workflow:view', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5020, 0, 'CATALOG', 'REPORT', '报表中心', '/reports', 'Layout', NULL, 7, 1, 'ACTIVE', 0, 0, 0, 0),
    (5021, 5020, 'MENU', 'REPORT_PURCHASE_ORDER', '采购订单报表', '/reports/purchase-orders', 'report/purchase-order/index', 'report:view', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5022, 5020, 'MENU', 'REPORT_SALES_ORDER', '销售订单报表', '/reports/sales-orders', 'report/sales-order/index', 'report:view', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5023, 5020, 'MENU', 'REPORT_INVENTORY_BALANCE', '库存余额报表', '/reports/inventory-balances', 'report/inventory-balance/index', 'report:view', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5024, 5020, 'MENU', 'REPORT_FINANCE_SETTLEMENT', '应收应付报表', '/reports/finance-settlements', 'report/finance-settlement/index', 'report:view', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    (5025, 5020, 'MENU', 'REPORT_INVENTORY_TRANSACTION', '库存流水报表', '/reports/inventory-transactions', 'report/inventory-transaction/index', 'report:view', 5, 1, 'ACTIVE', 0, 0, 0, 0)
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
    deleted_flag = VALUES(deleted_flag);

INSERT INTO sys_role_menu
(id, role_id, menu_id, created_by)
VALUES
    (7001, 3002, 5001, 0),
    (7002, 3002, 5002, 0),
    (7003, 3002, 5003, 0),
    (7004, 3002, 5004, 0),
    (7005, 3002, 5005, 0),
    (7006, 3002, 5006, 0),
    (7007, 3002, 5007, 0),
    (7008, 3002, 5008, 0),
    (7009, 3002, 5009, 0),
    (7010, 3002, 5010, 0),
    (7011, 3002, 5011, 0),
    (7012, 3002, 5012, 0),
    (7013, 3002, 5013, 0),
    (7014, 3002, 5014, 0),
    (7015, 3002, 5015, 0),
    (7016, 3002, 5020, 0),
    (7017, 3002, 5021, 0),
    (7018, 3002, 5022, 0),
    (7019, 3002, 5023, 0),
    (7020, 3002, 5024, 0),
    (7021, 3002, 5025, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
