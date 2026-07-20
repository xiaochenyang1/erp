-- V100: 修复 V86↔V87 菜单主键冲突导致手工凭证菜单/按钮被覆盖。
-- 缺陷:V86 用 sys_menu id 5104-5107 播种财务"手工凭证"菜单及 manage/approve/post 按钮,
-- V87(更晚)复用同一批 id 5104-5107 播种库存"补货建议"菜单/按钮,其 ON DUPLICATE KEY UPDATE
-- 覆盖了 parent_id/menu_type/menu_name/path/permission(但不含 menu_code)。最终这四行成为
-- 半财务半库存的错乱行:menu_code 仍是 FINANCE_VOUCHER_*,其余字段是库存补货。
-- 后果:①"手工凭证"菜单(/finance/vouchers/manual)与 finance:voucher:manage/approve/post
-- 三个按钮节点在库中不存在 → 非超管角色看不到手工凭证页、拿不到这三个按钮权限
-- (超管走全树+反射权限,不受影响,故此前 smoke 能过);②库存补货四节点 menu_code 是错的。
-- sys_role_menu 7148-7151 同样被 V87 复用,已正确指向库存补货节点,无需改。
--
-- 修复:用未占用 id 段(菜单 5310+、role_menu 7320+,均在 V98 的 5306/7316 之后)重建
-- 财务手工凭证菜单+按钮并绑定 ERP_ADMIN(3002);同时纠正库存补货四节点被残留的错误 menu_code。

-- 1) 纠正被 V87 覆盖后残留的错误 menu_code(这四行现为库存补货节点)。
UPDATE sys_menu SET menu_code = 'INVENTORY_REPLENISHMENT'        WHERE id = 5104 AND permission = 'inventory:replenishment:view';
UPDATE sys_menu SET menu_code = 'INVENTORY_REPLENISHMENT_CREATE'  WHERE id = 5105 AND permission = 'inventory:replenishment:create';
UPDATE sys_menu SET menu_code = 'INVENTORY_REPLENISHMENT_CANCEL'  WHERE id = 5106 AND permission = 'inventory:replenishment:cancel';
UPDATE sys_menu SET menu_code = 'INVENTORY_REPLENISHMENT_CONVERT' WHERE id = 5107 AND permission = 'inventory:replenishment:convert';

-- 2) 重建财务手工凭证菜单(MENU 挂在财务管理 CATALOG 5030 下)与三个操作按钮
--    (BUTTON 挂在记账凭证 MENU 5033 下,忠实还原 V86 原始结构)。
INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5310, 5030, 'MENU', 'FINANCE_MANUAL_VOUCHER', '手工凭证', '/finance/vouchers/manual',
     'finance/vouchers/manual/index', 'finance:voucher:view', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    (5311, 5033, 'BUTTON', 'FINANCE_VOUCHER_MANAGE', '录入手工凭证', NULL, NULL,
     'finance:voucher:manage', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5312, 5033, 'BUTTON', 'FINANCE_VOUCHER_APPROVE', '审批手工凭证', NULL, NULL,
     'finance:voucher:approve', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5313, 5033, 'BUTTON', 'FINANCE_VOUCHER_POST', '过账手工凭证', NULL, NULL,
     'finance:voucher:post', 3, 1, 'ACTIVE', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_type = VALUES(menu_type),
    menu_code = VALUES(menu_code),
    menu_name = VALUES(menu_name),
    path = VALUES(path),
    component = VALUES(component),
    permission = VALUES(permission),
    sort_no = VALUES(sort_no),
    visible_flag = VALUES(visible_flag),
    status = VALUES(status),
    deleted_flag = VALUES(deleted_flag),
    updated_by = VALUES(updated_by);

-- 3) 绑定给 ERP_ADMIN(3002)(SUPER_ADMIN 走全树无需绑)。
INSERT INTO sys_role_menu
(id, role_id, menu_id, created_by)
VALUES
    (7320, 3002, 5310, 0),
    (7321, 3002, 5311, 0),
    (7322, 3002, 5312, 0),
    (7323, 3002, 5313, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
