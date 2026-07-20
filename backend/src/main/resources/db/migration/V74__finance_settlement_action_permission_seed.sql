INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5044, 5036, 'BUTTON', 'FINANCE_PAYMENT_CREATE', '创建付款单', NULL,
     NULL, 'finance:payment:create', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5045, 5036, 'BUTTON', 'FINANCE_PAYMENT_CANCEL', '作废付款单', NULL,
     NULL, 'finance:payment:cancel', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5046, 5035, 'BUTTON', 'FINANCE_RECEIPT_CREATE', '创建收款单', NULL,
     NULL, 'finance:receipt:create', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5047, 5035, 'BUTTON', 'FINANCE_RECEIPT_CANCEL', '作废收款单', NULL,
     NULL, 'finance:receipt:cancel', 2, 1, 'ACTIVE', 0, 0, 0, 0)
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
    (7134, 3002, 5044, 0),
    (7135, 3002, 5045, 0),
    (7136, 3002, 5046, 0),
    (7137, 3002, 5047, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
