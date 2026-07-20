INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5035, 5030, 'MENU', 'FINANCE_RECEIVABLE', '应收管理', '/finance/receivables',
     'finance/receivable/index', 'finance:receivable:view', 5, 1, 'ACTIVE', 0, 0, 0, 0),
    (5036, 5030, 'MENU', 'FINANCE_PAYABLE', '应付管理', '/finance/payables',
     'finance/payable/index', 'finance:payable:view', 6, 1, 'ACTIVE', 0, 0, 0, 0)
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

INSERT INTO sys_role_menu
(id, role_id, menu_id, created_by)
VALUES
    (7055, 3002, 5035, 0),
    (7056, 3002, 5036, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
