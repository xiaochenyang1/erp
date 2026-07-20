INSERT INTO sys_sequence_rule
(id, biz_type, prefix, date_pattern, seq_length, current_value, status, created_by, updated_by, version)
VALUES
    (2020, 'PRODUCTION_BOM', 'PB', 'yyyyMMdd', 4, 1, 'ACTIVE', 0, 0, 0),
    (2021, 'PRODUCTION_ORDER', 'MO', 'yyyyMMdd', 4, 1, 'ACTIVE', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    prefix = VALUES(prefix),
    date_pattern = VALUES(date_pattern),
    seq_length = VALUES(seq_length),
    current_value = GREATEST(current_value, VALUES(current_value)),
    status = VALUES(status);

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5080, 0, 'CATALOG', 'PRODUCTION', '生产管理', '/production', 'Layout', NULL, 8, 1, 'ACTIVE', 0, 0, 0, 0),
    (5081, 5080, 'MENU', 'PRODUCTION_BOM', 'BOM管理', '/production/boms', 'production/bom/index', 'production:bom:view', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5082, 5081, 'BUTTON', 'PRODUCTION_BOM_MANAGE', '维护BOM', NULL, NULL, 'production:bom:manage', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5083, 5080, 'MENU', 'PRODUCTION_ORDER', '生产工单', '/production/orders', 'production/order/index', 'production:order:view', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5084, 5083, 'BUTTON', 'PRODUCTION_ORDER_CREATE', '创建生产工单', NULL, NULL, 'production:order:create', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5085, 5083, 'BUTTON', 'PRODUCTION_ORDER_UPDATE', '修改生产工单', NULL, NULL, 'production:order:update', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5086, 5083, 'BUTTON', 'PRODUCTION_ORDER_RELEASE', '释放生产工单', NULL, NULL, 'production:order:release', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5087, 5083, 'BUTTON', 'PRODUCTION_ORDER_ISSUE', '生产领料', NULL, NULL, 'production:order:issue', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    (5088, 5083, 'BUTTON', 'PRODUCTION_ORDER_COMPLETE', '生产完工', NULL, NULL, 'production:order:complete', 5, 1, 'ACTIVE', 0, 0, 0, 0),
    (5089, 5083, 'BUTTON', 'PRODUCTION_ORDER_CANCEL', '取消生产工单', NULL, NULL, 'production:order:cancel', 6, 1, 'ACTIVE', 0, 0, 0, 0)
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
    (7100, 3002, 5080, 0),
    (7101, 3002, 5081, 0),
    (7102, 3002, 5082, 0),
    (7103, 3002, 5083, 0),
    (7104, 3002, 5084, 0),
    (7105, 3002, 5085, 0),
    (7106, 3002, 5086, 0),
    (7107, 3002, 5087, 0),
    (7108, 3002, 5088, 0),
    (7109, 3002, 5089, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
