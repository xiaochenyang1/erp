-- V98: 第三批按钮级权限收口配套菜单种子（finance 三页）。
-- 为 finance/funds(资金对账)、finance/payments(收付款管理)、finance/subjects(会计科目)
-- 补 BUTTON 权限节点，permission 与后端 @PreAuthorize 及前端 v-permission 三方对齐，
-- 并绑定给 ERP_ADMIN(3002)。SUPER_ADMIN(3001) 走全树，无需绑定。
-- 号段：menu id 5300+（V97 QC 用至 5295），role_menu id 7310+（V97 用至 7305），ON DUPLICATE KEY UPDATE 幂等。
-- 父 MENU：finance/funds=5041 finance/payments=5147 finance/subjects=5031

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    -- 资金对账（资金账户创建/银行流水创建 = manage；流水匹配/取消匹配 = reconcile）
    (5300, 5041, 'BUTTON', 'FINANCE_FUND_MANAGE', '资金账户与流水管理', NULL, NULL, 'finance:fund:manage', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5301, 5041, 'BUTTON', 'FINANCE_FUND_RECONCILE', '银行流水对账匹配', NULL, NULL, 'finance:fund:reconcile', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    -- 收付款管理（收款单）
    (5302, 5147, 'BUTTON', 'FINANCE_RECEIPT_CREATE', '新增收款单', NULL, NULL, 'finance:receipt:create', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5303, 5147, 'BUTTON', 'FINANCE_RECEIPT_CANCEL', '取消收款单', NULL, NULL, 'finance:receipt:cancel', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    -- 收付款管理（付款单）
    (5304, 5147, 'BUTTON', 'FINANCE_PAYMENT_CREATE', '新增付款单', NULL, NULL, 'finance:payment:create', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5305, 5147, 'BUTTON', 'FINANCE_PAYMENT_CANCEL', '取消付款单', NULL, NULL, 'finance:payment:cancel', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    -- 会计科目（新增/新增下级/编辑/启停均为同一 manage 码）
    (5306, 5031, 'BUTTON', 'FINANCE_SUBJECT_MANAGE', '会计科目维护', NULL, NULL, 'finance:subject:manage', 1, 1, 'ACTIVE', 0, 0, 0, 0)
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
    (7310, 3002, 5300, 0), (7311, 3002, 5301, 0),
    (7312, 3002, 5302, 0), (7313, 3002, 5303, 0),
    (7314, 3002, 5304, 0), (7315, 3002, 5305, 0),
    (7316, 3002, 5306, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
