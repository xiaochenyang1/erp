-- 补齐三个已被 Controller 使用、但历史菜单种子遗漏的权限码。
-- SUPER_ADMIN(3001) 通过反射权限集不受影响；ERP_ADMIN(3002) 和自定义角色的
-- 权限来自 sys_role_menu，缺少这些节点会分别导致库存规则创建、收款查询、
-- 流水号规则查询在非超级管理员下 403。
-- 号段：sys_menu 5420-5422（V121 已用至 5412），sys_role_menu 7430-7432（V121 已用至 7422）。

-- V85 的库存预警处置按钮早于正式菜单节点创建，历史 parent_id=505 在 Flyway
-- 初始化链路中没有对应父节点；迁到 V94 建立的库存预警菜单下，便于角色授权树展示。
UPDATE sys_menu
SET parent_id = 5146,
    updated_by = 0
WHERE permission = 'inventory:alert:handle'
  AND menu_type = 'BUTTON'
  AND deleted_flag = 0;

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5420, 5146, 'BUTTON', 'INVENTORY_ALERT_CREATE', '创建库存预警规则', NULL, NULL,
     'inventory:alert:create', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5421, 5147, 'BUTTON', 'FINANCE_RECEIPT_VIEW', '查看收款单', NULL, NULL,
     'finance:receipt:view', 0, 1, 'ACTIVE', 0, 0, 0, 0),
    (5422, 5148, 'BUTTON', 'SYSTEM_SEQUENCE_RULE_VIEW', '查看流水号规则', NULL, NULL,
     'system:sequence-rule:view', 0, 1, 'ACTIVE', 0, 0, 0, 0)
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
    (7430, 3002, 5420, 0),
    (7431, 3002, 5421, 0),
    (7432, 3002, 5422, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
