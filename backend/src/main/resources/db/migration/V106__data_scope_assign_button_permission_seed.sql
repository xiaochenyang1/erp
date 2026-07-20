-- V106: 用户/角色「配置数据范围」独立写权限码 BUTTON 种子。
-- 背景:数据范围写接口从 system:user:update / system:role:update 收紧为
--   system:user:assign-data-scope / system:role:assign-data-scope。
-- SUPER_ADMIN(3001) 走反射全权限不受影响;ERP_ADMIN(3002) 权限来自 sys_role_menu,
-- 须补 BUTTON 并绑定,否则收紧后点「数据范围」保存会 403。
-- 号段:menu id 5330-5331(全局已占用至 5323=V102;5320=V101),role_menu id 7340-7341(已占用至 7334=V103)。
-- 父 MENU:用户 5002 / 角色 5003。ON DUPLICATE KEY UPDATE 幂等。

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5330, 5002, 'BUTTON', 'SYSTEM_USER_ASSIGN_DATA_SCOPE', '配置用户数据范围', NULL, NULL, 'system:user:assign-data-scope', 6, 1, 'ACTIVE', 0, 0, 0, 0),
    (5331, 5003, 'BUTTON', 'SYSTEM_ROLE_ASSIGN_DATA_SCOPE', '配置角色数据范围', NULL, NULL, 'system:role:assign-data-scope', 6, 1, 'ACTIVE', 0, 0, 0, 0)
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
    (7340, 3002, 5330, 0),
    (7341, 3002, 5331, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
