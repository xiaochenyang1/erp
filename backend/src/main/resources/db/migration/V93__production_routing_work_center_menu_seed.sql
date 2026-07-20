INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5111, 5080, 'MENU', 'PRODUCTION_WORK_CENTER', '工作中心', '/production/work-centers',
     'production/work-center/index', 'production:work-center:view', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5112, 5111, 'BUTTON', 'PRODUCTION_WORK_CENTER_CREATE', '创建工作中心', NULL, NULL,
     'production:work-center:create', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5113, 5111, 'BUTTON', 'PRODUCTION_WORK_CENTER_UPDATE', '修改工作中心', NULL, NULL,
     'production:work-center:update', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5114, 5111, 'BUTTON', 'PRODUCTION_WORK_CENTER_ENABLE', '启用工作中心', NULL, NULL,
     'production:work-center:enable', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5115, 5111, 'BUTTON', 'PRODUCTION_WORK_CENTER_DISABLE', '停用工作中心', NULL, NULL,
     'production:work-center:disable', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    (5116, 5080, 'MENU', 'PRODUCTION_ROUTING', '工艺路线', '/production/routings',
     'production/routing/index', 'production:routing:view', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    (5117, 5116, 'BUTTON', 'PRODUCTION_ROUTING_CREATE', '创建工艺路线', NULL, NULL,
     'production:routing:create', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5118, 5116, 'BUTTON', 'PRODUCTION_ROUTING_UPDATE', '修改工艺路线', NULL, NULL,
     'production:routing:update', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5119, 5116, 'BUTTON', 'PRODUCTION_ROUTING_ENABLE', '启用工艺路线', NULL, NULL,
     'production:routing:enable', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5120, 5116, 'BUTTON', 'PRODUCTION_ROUTING_DISABLE', '停用工艺路线', NULL, NULL,
     'production:routing:disable', 4, 1, 'ACTIVE', 0, 0, 0, 0)
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
    (7155, 3002, 5111, 0),
    (7156, 3002, 5112, 0),
    (7157, 3002, 5113, 0),
    (7158, 3002, 5114, 0),
    (7159, 3002, 5115, 0),
    (7160, 3002, 5116, 0),
    (7161, 3002, 5117, 0),
    (7162, 3002, 5118, 0),
    (7163, 3002, 5119, 0),
    (7164, 3002, 5120, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
