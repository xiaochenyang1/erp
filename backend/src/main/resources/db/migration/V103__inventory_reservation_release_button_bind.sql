-- V103: 按钮级权限收口收尾 —— 手工释放库存预占按钮绑定 ERP_ADMIN(3002)。
-- 背景:BUTTON 节点 5018(INVENTORY_RESERVATION_RELEASE / inventory:reservation:release)自 V33 起就存在,
--   后端 InventoryReservationOpsController 的 release 端点也早已挂 @PreAuthorize(HAS_INVENTORY_RESERVATION_RELEASE),
--   但该 BUTTON 从未写入 sys_role_menu 绑给任何非超管角色。
-- SUPER_ADMIN(3001) 走反射全权限 + 全树,不受影响;ERP_ADMIN(3002) 权限来自 sys_role_menu,
--   若不补绑定,前端库存查询页的"释放"按钮挂 v-permission 后会对 3002 直接隐藏。此处补绑收口。
-- 号段:role_menu id 7334(全局已占用至 7333=V102),ON DUPLICATE KEY UPDATE 幂等。
--       menu 节点 5018 已存在,不重复插入,避免重蹈 V86 复用已占用主键覆盖既有节点的地雷。

INSERT INTO sys_role_menu
(id, role_id, menu_id, created_by)
VALUES
    (7334, 3002, 5018, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
