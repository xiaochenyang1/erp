-- V97: 采购来料质检菜单种子。
-- 质检页挂在采购 CATALOG(5135) 下,sort 4;menu id 用 5290 段(V95 用至 5284),
-- role_menu id 用 7300 段(V95 用至 7289)。permission 与 QcPermissionCodes/@PreAuthorize/前端 v-permission 三方对齐。
-- SUPER_ADMIN(3001) 走全树,无需绑定;此处绑 ERP_ADMIN(3002)。ON DUPLICATE KEY UPDATE 幂等。

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5290, 5135, 'MENU', 'QC_INSPECTION', '来料检验', '/qc/inspections',
     'qc/inspection/index', 'qc:inspection:view', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    (5291, 5290, 'BUTTON', 'QC_INSPECTION_CREATE', '创建检验单', NULL, NULL,
     'qc:inspection:create', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5292, 5290, 'BUTTON', 'QC_INSPECTION_UPDATE', '修改检验单', NULL, NULL,
     'qc:inspection:update', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5293, 5290, 'BUTTON', 'QC_INSPECTION_SUBMIT', '提交检验单', NULL, NULL,
     'qc:inspection:submit', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5294, 5290, 'BUTTON', 'QC_INSPECTION_JUDGE', '判定检验单', NULL, NULL,
     'qc:inspection:judge', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    (5295, 5290, 'BUTTON', 'QC_INSPECTION_CANCEL', '作废检验单', NULL, NULL,
     'qc:inspection:cancel', 5, 1, 'ACTIVE', 0, 0, 0, 0)
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
    (7300, 3002, 5290, 0),
    (7301, 3002, 5291, 0),
    (7302, 3002, 5292, 0),
    (7303, 3002, 5293, 0),
    (7304, 3002, 5294, 0),
    (7305, 3002, 5295, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
