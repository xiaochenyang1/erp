USE erp_server;

INSERT INTO sys_role (id, role_code, role_name)
VALUES
    (3001, 'SUPER_ADMIN', '超级管理员'),
    (3002, 'ERP_ADMIN', '企业管理员')
ON DUPLICATE KEY UPDATE
    role_name = VALUES(role_name),
    status = 'ACTIVE',
    deleted_flag = 0;

INSERT INTO sys_dept (id, parent_id, dept_code, dept_name, leader_user_id, sort_no, status, remark)
VALUES
    (3501, 0, 'HEAD_OFFICE', '总部', 4001, 1, 'ACTIVE', '系统默认根部门')
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    dept_name = VALUES(dept_name),
    leader_user_id = VALUES(leader_user_id),
    sort_no = VALUES(sort_no),
    status = VALUES(status),
    deleted_flag = 0;

INSERT INTO sys_post (id, dept_id, post_code, post_name, status, remark)
VALUES
    (3601, 3501, 'ADMIN_POST', '系统管理员岗位', 'ACTIVE', '系统默认岗位')
ON DUPLICATE KEY UPDATE
    dept_id = VALUES(dept_id),
    post_name = VALUES(post_name),
    status = VALUES(status),
    deleted_flag = 0;

INSERT INTO sys_user (id, username, password, employee_no, real_name, dept_id, post_id, status)
VALUES
    (4001, 'admin', '$2a$10$Ms6YLk8eBEPKgRWKFCr0nOrQTg6cX9mXsJmptcPSFrCZwbjtR6/Gu', 'EMP_ADMIN', '系统管理员', 3501, 3601, 'ACTIVE')
ON DUPLICATE KEY UPDATE
    employee_no = VALUES(employee_no),
    real_name = VALUES(real_name),
    dept_id = VALUES(dept_id),
    post_id = VALUES(post_id),
    status = VALUES(status),
    deleted_flag = 0;

INSERT INTO md_warehouse (id, warehouse_code, warehouse_name, dept_id, manager_user_id, address, status, remark)
VALUES
    (4501, 'MAIN_WH', '默认主仓', 3501, 4001, '总部默认仓库地址', 'ACTIVE', '系统初始化默认仓库')
ON DUPLICATE KEY UPDATE
    warehouse_name = VALUES(warehouse_name),
    dept_id = VALUES(dept_id),
    manager_user_id = VALUES(manager_user_id),
    address = VALUES(address),
    status = VALUES(status),
    deleted_flag = 0,
    remark = VALUES(remark);

INSERT INTO md_product (
    id,
    product_code,
    product_name,
    product_type,
    category_name,
    specification,
    unit_name,
    purchase_price,
    sale_price,
    tax_rate,
    status,
    remark
)
VALUES
    (4601, 'DEMO_SKU_001', '演示商品A', 'PHYSICAL', '演示分类', '标准版', '件', 100.00, 120.00, 13.0000, 'ACTIVE', '系统初始化演示商品')
ON DUPLICATE KEY UPDATE
    product_name = VALUES(product_name),
    product_type = VALUES(product_type),
    category_name = VALUES(category_name),
    specification = VALUES(specification),
    unit_name = VALUES(unit_name),
    purchase_price = VALUES(purchase_price),
    sale_price = VALUES(sale_price),
    tax_rate = VALUES(tax_rate),
    status = VALUES(status),
    deleted_flag = 0,
    remark = VALUES(remark);

INSERT INTO md_customer (
    id,
    customer_code,
    customer_name,
    contact_name,
    contact_phone,
    settlement_method,
    credit_limit,
    address,
    status,
    remark
)
VALUES
    (4602, 'DEMO_CUST_001', '演示客户A', '张经理', '13888880001', 'MONTH_END_30', 50000.00, '总部默认演示客户地址', 'ACTIVE', '系统初始化演示客户')
ON DUPLICATE KEY UPDATE
    customer_name = VALUES(customer_name),
    contact_name = VALUES(contact_name),
    contact_phone = VALUES(contact_phone),
    settlement_method = VALUES(settlement_method),
    credit_limit = VALUES(credit_limit),
    address = VALUES(address),
    status = VALUES(status),
    deleted_flag = 0,
    remark = VALUES(remark);

INSERT INTO md_supplier (
    id,
    supplier_code,
    supplier_name,
    contact_name,
    contact_phone,
    settlement_method,
    address,
    status,
    remark
)
VALUES
    (4603, 'DEMO_SUPP_001', '演示供应商A', '李主管', '13777770001', 'MONTH_END_30', '总部默认演示供应商地址', 'ACTIVE', '系统初始化演示供应商')
ON DUPLICATE KEY UPDATE
    supplier_name = VALUES(supplier_name),
    contact_name = VALUES(contact_name),
    contact_phone = VALUES(contact_phone),
    settlement_method = VALUES(settlement_method),
    address = VALUES(address),
    status = VALUES(status),
    deleted_flag = 0,
    remark = VALUES(remark);

INSERT INTO pur_order (
    id,
    company_id,
    account_book_id,
    order_no,
    supplier_id,
    order_date,
    delivery_date,
    status,
    approval_status,
    receipt_status,
    total_quantity,
    total_amount,
    total_tax_amount,
    remark
)
VALUES
    (4701, 1, 1, 'PO202604280001', 4603, '2026-04-28', '2026-05-05', 'APPROVED', 'APPROVED', 'PARTIAL_RECEIVED', 10.0000, 1000.00, 130.00, '系统初始化已审批演示采购订单')
ON DUPLICATE KEY UPDATE
    supplier_id = VALUES(supplier_id),
    order_date = VALUES(order_date),
    delivery_date = VALUES(delivery_date),
    status = VALUES(status),
    approval_status = VALUES(approval_status),
    receipt_status = VALUES(receipt_status),
    total_quantity = VALUES(total_quantity),
    total_amount = VALUES(total_amount),
    total_tax_amount = VALUES(total_tax_amount),
    deleted_flag = 0,
    remark = VALUES(remark);

INSERT INTO pur_order_line (
    id,
    order_id,
    line_no,
    product_id,
    qty,
    price,
    tax_rate,
    tax_amount,
    amount,
    received_qty,
    remark
)
VALUES
    (4702, 4701, 1, 4601, 10.0000, 100.00, 13.0000, 130.00, 1000.00, 3.0000, '系统初始化演示采购订单明细')
ON DUPLICATE KEY UPDATE
    order_id = VALUES(order_id),
    line_no = VALUES(line_no),
    product_id = VALUES(product_id),
    qty = VALUES(qty),
    price = VALUES(price),
    tax_rate = VALUES(tax_rate),
    tax_amount = VALUES(tax_amount),
    amount = VALUES(amount),
    received_qty = VALUES(received_qty),
    remark = VALUES(remark);

INSERT INTO pur_receipt (
    id,
    company_id,
    account_book_id,
    receipt_no,
    order_id,
    warehouse_id,
    receipt_date,
    status,
    total_quantity,
    total_amount,
    total_tax_amount,
    remark
)
VALUES
    (4801, 1, 1, 'PR202604300001', 4701, 4501, '2026-04-30', 'POSTED', 3.0000, 300.00, 39.00, '系统初始化演示采购入库单')
ON DUPLICATE KEY UPDATE
    order_id = VALUES(order_id),
    warehouse_id = VALUES(warehouse_id),
    receipt_date = VALUES(receipt_date),
    status = VALUES(status),
    total_quantity = VALUES(total_quantity),
    total_amount = VALUES(total_amount),
    total_tax_amount = VALUES(total_tax_amount),
    deleted_flag = 0,
    remark = VALUES(remark);

INSERT INTO pur_receipt_line (
    id,
    receipt_id,
    line_no,
    order_line_id,
    product_id,
    qty,
    price,
    tax_rate,
    amount,
    tax_amount,
    returned_qty,
    remark
)
VALUES
    (4802, 4801, 1, 4702, 4601, 3.0000, 100.00, 13.0000, 300.00, 39.00, 0.0000, '系统初始化演示采购入库明细')
ON DUPLICATE KEY UPDATE
    receipt_id = VALUES(receipt_id),
    line_no = VALUES(line_no),
    order_line_id = VALUES(order_line_id),
    product_id = VALUES(product_id),
    qty = VALUES(qty),
    price = VALUES(price),
    tax_rate = VALUES(tax_rate),
    amount = VALUES(amount),
    tax_amount = VALUES(tax_amount),
    returned_qty = VALUES(returned_qty),
    remark = VALUES(remark);

INSERT INTO pur_return (
    id,
    company_id,
    account_book_id,
    return_no,
    receipt_id,
    warehouse_id,
    return_date,
    status,
    total_quantity,
    total_amount,
    total_tax_amount,
    remark
)
VALUES
    (4805, 1, 1, 'PRT202605010001', 4801, 4501, '2026-05-01', 'DRAFT', 1.0000, 100.00, 13.00, '系统初始化演示采购退货单')
ON DUPLICATE KEY UPDATE
    receipt_id = VALUES(receipt_id),
    warehouse_id = VALUES(warehouse_id),
    return_date = VALUES(return_date),
    status = VALUES(status),
    total_quantity = VALUES(total_quantity),
    total_amount = VALUES(total_amount),
    total_tax_amount = VALUES(total_tax_amount),
    deleted_flag = 0,
    remark = VALUES(remark);

INSERT INTO pur_return_line (
    id,
    return_id,
    line_no,
    receipt_line_id,
    order_line_id,
    product_id,
    qty,
    price,
    tax_rate,
    amount,
    tax_amount,
    remark
)
VALUES
    (4806, 4805, 1, 4802, 4702, 4601, 1.0000, 100.00, 13.0000, 100.00, 13.00, '系统初始化演示采购退货明细')
ON DUPLICATE KEY UPDATE
    return_id = VALUES(return_id),
    line_no = VALUES(line_no),
    receipt_line_id = VALUES(receipt_line_id),
    order_line_id = VALUES(order_line_id),
    product_id = VALUES(product_id),
    qty = VALUES(qty),
    price = VALUES(price),
    tax_rate = VALUES(tax_rate),
    amount = VALUES(amount),
    tax_amount = VALUES(tax_amount),
    remark = VALUES(remark);

INSERT INTO inv_balance (
    id,
    warehouse_id,
    product_id,
    qty_on_hand,
    qty_reserved,
    amount_on_hand
)
VALUES
    (4803, 4501, 4601, 3.0000, 0.0000, 300.00)
ON DUPLICATE KEY UPDATE
    warehouse_id = VALUES(warehouse_id),
    product_id = VALUES(product_id),
    qty_on_hand = VALUES(qty_on_hand),
    qty_reserved = VALUES(qty_reserved),
    amount_on_hand = VALUES(amount_on_hand);

INSERT INTO inv_txn (
    id,
    warehouse_id,
    product_id,
    biz_type,
    biz_no,
    biz_line_id,
    direction,
    qty,
    amount,
    unit_cost,
    occurred_time,
    remark
)
VALUES
    (4804, 4501, 4601, 'PURCHASE_RECEIPT', 'PR202604300001', 4802, 'IN', 3.0000, 300.00, 100.0000, '2026-04-30 10:00:00', '系统初始化演示库存流水')
ON DUPLICATE KEY UPDATE
    warehouse_id = VALUES(warehouse_id),
    product_id = VALUES(product_id),
    biz_type = VALUES(biz_type),
    biz_no = VALUES(biz_no),
    biz_line_id = VALUES(biz_line_id),
    direction = VALUES(direction),
    qty = VALUES(qty),
    amount = VALUES(amount),
    unit_cost = VALUES(unit_cost),
    occurred_time = VALUES(occurred_time),
    remark = VALUES(remark);

INSERT INTO sys_menu (id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no)
VALUES
    (5001, 0, 'CATALOG', 'SYSTEM', '系统管理', '/system', 'Layout', NULL, 1),
    (5002, 5001, 'MENU', 'SYSTEM_USER', '用户管理', '/system/users', 'system/user/index', 'system:user:view', 1),
    (5003, 5001, 'MENU', 'SYSTEM_ROLE', '角色管理', '/system/roles', 'system/role/index', 'system:role:view', 2),
    (5004, 5001, 'MENU', 'SYSTEM_MENU', '菜单管理', '/system/menus', 'system/menu/index', 'system:menu:view', 3),
    (5005, 5001, 'MENU', 'SYSTEM_DEPT', '部门管理', '/system/depts', 'system/dept/index', 'system:dept:view', 4),
    (5006, 5001, 'MENU', 'SYSTEM_POST', '岗位管理', '/system/posts', 'system/post/index', 'system:post:view', 5),
    (5007, 5001, 'MENU', 'SYSTEM_DICT', '字典管理', '/system/dicts', 'system/dict/index', 'system:dict:view', 6),
    (5008, 5001, 'MENU', 'SYSTEM_LOG', '日志管理', '/system/logs', 'system/log/index', 'system:log:view', 7),
    (5009, 0, 'CATALOG', 'INVENTORY', '库存管理', '/inventory', 'Layout', NULL, 5),
    (5013, 5009, 'MENU', 'INVENTORY_TRANSFER', '库存调拨', '/inventory/transfers', 'inventory/transfer/index', 'inventory:transfer:view', 4),
    (5014, 5013, 'BUTTON', 'INVENTORY_TRANSFER_CREATE', '创建库存调拨', NULL, NULL, 'inventory:transfer:create', 1),
    (5015, 5013, 'BUTTON', 'INVENTORY_TRANSFER_POST', '过账库存调拨', NULL, NULL, 'inventory:transfer:post', 2),
    (5010, 0, 'CATALOG', 'WORKFLOW', '审批中心', '/workflow', 'Layout', NULL, 6),
    (5011, 5010, 'MENU', 'WORKFLOW_TASK', '审批待办', '/workflow/tasks', 'workflow/task/index', 'workflow:view', 1),
    (5012, 5010, 'MENU', 'WORKFLOW_RECORD', '审批记录', '/workflow/records', 'workflow/record/index', 'workflow:view', 2),
    (5020, 0, 'CATALOG', 'REPORT', '报表中心', '/reports', 'Layout', NULL, 7),
    (5021, 5020, 'MENU', 'REPORT_PURCHASE_ORDER', '采购订单报表', '/reports/purchase-orders', 'report/purchase-order/index', 'report:view', 1),
    (5022, 5020, 'MENU', 'REPORT_SALES_ORDER', '销售订单报表', '/reports/sales-orders', 'report/sales-order/index', 'report:view', 2),
    (5023, 5020, 'MENU', 'REPORT_INVENTORY_BALANCE', '库存余额报表', '/reports/inventory-balances', 'report/inventory-balance/index', 'report:view', 3),
    (5024, 5020, 'MENU', 'REPORT_FINANCE_SETTLEMENT', '应收应付报表', '/reports/finance-settlements', 'report/finance-settlement/index', 'report:view', 4),
    (5025, 5020, 'MENU', 'REPORT_INVENTORY_TRANSACTION', '库存流水报表', '/reports/inventory-transactions', 'report/inventory-transaction/index', 'report:view', 5)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_type = VALUES(menu_type),
    menu_name = VALUES(menu_name),
    path = VALUES(path),
    component = VALUES(component),
    permission = VALUES(permission),
    sort_no = VALUES(sort_no),
    status = 'ACTIVE',
    deleted_flag = 0;

INSERT INTO sys_user_role (id, user_id, role_id)
VALUES
    (6001, 4001, 3001)
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    role_id = VALUES(role_id);

INSERT INTO sys_role_menu (id, role_id, menu_id)
VALUES
    (7001, 3001, 5001),
    (7002, 3001, 5002),
    (7003, 3001, 5003),
    (7004, 3001, 5004),
    (7005, 3001, 5005),
    (7006, 3001, 5006),
    (7029, 3001, 5007),
    (7030, 3001, 5008),
    (7035, 3001, 5009),
    (7036, 3001, 5013),
    (7037, 3001, 5014),
    (7038, 3001, 5015),
    (7007, 3001, 5010),
    (7008, 3001, 5011),
    (7009, 3001, 5012),
    (7010, 3001, 5020),
    (7011, 3001, 5021),
    (7012, 3001, 5022),
    (7013, 3001, 5023),
    (7014, 3001, 5024),
    (7031, 3001, 5025),
    (7015, 3002, 5001),
    (7016, 3002, 5002),
    (7017, 3002, 5003),
    (7018, 3002, 5004),
    (7019, 3002, 5005),
    (7020, 3002, 5006),
    (7032, 3002, 5007),
    (7033, 3002, 5008),
    (7039, 3002, 5009),
    (7040, 3002, 5013),
    (7041, 3002, 5014),
    (7042, 3002, 5015),
    (7021, 3002, 5010),
    (7022, 3002, 5011),
    (7023, 3002, 5012),
    (7024, 3002, 5020),
    (7025, 3002, 5021),
    (7026, 3002, 5022),
    (7027, 3002, 5023),
    (7028, 3002, 5024),
    (7034, 3002, 5025)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
