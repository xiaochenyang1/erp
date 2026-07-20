-- V114: 往来对账单 + 毛利简报菜单（只读）
-- menu 5390 statements, 5391 margin; role_menu 7400-7401

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5390, 5030, 'MENU', 'FINANCE_STATEMENT', '往来对账', '/finance/statements',
     'finance/statements/index', 'finance:statement:view', 12, 1, 'ACTIVE', 0, 0, 0, 0),
    (5391, 5030, 'MENU', 'FINANCE_MARGIN', '毛利简报', '/finance/gross-margin',
     'finance/gross-margin/index', 'finance:margin:view', 13, 1, 'ACTIVE', 0, 0, 0, 0)
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

INSERT INTO sys_role_menu (id, role_id, menu_id, created_by) VALUES
    (7400, 3002, 5390, 0),
    (7401, 3002, 5391, 0)
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id), menu_id = VALUES(menu_id);
