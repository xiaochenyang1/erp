-- V117: 轻量 MRP 计划菜单（挂库存 CATALOG 5009）
INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5410, 5009, 'MENU', 'INVENTORY_MRP', 'MRP计划', '/inventory/mrp',
     'inventory/mrp/index', 'inventory:mrp:view', 20, 1, 'ACTIVE', 0, 0, 0, 0),
    (5411, 5410, 'BUTTON', 'INVENTORY_MRP_RUN', '运行MRP', NULL, NULL,
     'inventory:mrp:run', 1, 1, 'ACTIVE', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_name = VALUES(menu_name),
    path = VALUES(path),
    component = VALUES(component),
    permission = VALUES(permission),
    status = VALUES(status),
    deleted_flag = VALUES(deleted_flag);

INSERT INTO sys_role_menu (id, role_id, menu_id, created_by) VALUES
    (7420, 3002, 5410, 0),
    (7421, 3002, 5411, 0)
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id), menu_id = VALUES(menu_id);
