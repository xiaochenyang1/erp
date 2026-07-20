-- 单据状态流转规则(只读)菜单种子
-- 背景：后端 GET /api/system/document-state-rules 早已就绪(system:config:view 门禁)但前端无入口。
--       runtime-menu-tree 驱动侧边栏：SUPER_ADMIN 走全树，ERP_ADMIN(3002) 走 role_menu，
--       故须补 sys_menu 节点并绑 3002，页面才会出现在侧边栏。
-- 号段：menu id 5320（全局已占用至 5313），role_menu id 7330（已占用至 7323），均 ON DUPLICATE KEY UPDATE 幂等。
--       只读页无写按钮，故只补 1 个 MENU 节点，不建 BUTTON。
INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5320, 5001, 'MENU', 'SYSTEM_DOCUMENT_STATE_RULE', '单据状态规则', '/system/document-state-rules',
     'system/document-state-rules/index', 'system:config:view', 14, 1, 'ACTIVE', 0, 0, 0, 0)
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
    (7330, 3002, 5320, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
