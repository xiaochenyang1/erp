-- V102: 按钮级权限收口配套菜单种子(通知标记已读 + 工作流审批/驳回)。
-- 背景:后端把三处原本借用只读码(notification:view / workflow:view)的写操作收紧为独立写权限码:
--   NotificationController.markRead / markAllRead -> system:notification:manage
--   WorkflowController.approveTask                -> workflow:approve
--   WorkflowController.rejectTask                 -> workflow:reject
-- SUPER_ADMIN(3001) 走反射全权限,不受影响;非超管角色(ERP_ADMIN=3002)权限来自 sys_role_menu,
-- 若不补 BUTTON 节点并绑定,收紧后 ERP_ADMIN 点"标记已读/审批/驳回"会直接 403。此处补种子收口。
-- 号段:menu id 5321-5323(全局已占用至 5320=V101),role_menu id 7331-7333(已占用至 7330=V101),
--       均 ON DUPLICATE KEY UPDATE 幂等。避免重蹈 V86 复用已占用主键覆盖既有节点的地雷。
-- 父 MENU:通知标记已读挂 5063(通知中心);审批/驳回挂 5011(审批待办)。

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    -- 通知中心:标记已读 / 全部已读
    (5321, 5063, 'BUTTON', 'SYSTEM_NOTIFICATION_MANAGE', '通知标记已读', NULL, NULL, 'system:notification:manage', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    -- 审批待办:审批通过
    (5322, 5011, 'BUTTON', 'WORKFLOW_APPROVE', '审批通过', NULL, NULL, 'workflow:approve', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    -- 审批待办:审批驳回
    (5323, 5011, 'BUTTON', 'WORKFLOW_REJECT', '审批驳回', NULL, NULL, 'workflow:reject', 2, 1, 'ACTIVE', 0, 0, 0, 0)
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

-- 绑定给 ERP_ADMIN(3002)。SUPER_ADMIN(3001) 走全树,无需绑定。
INSERT INTO sys_role_menu
(id, role_id, menu_id, created_by)
VALUES
    (7331, 3002, 5321, 0),
    (7332, 3002, 5322, 0),
    (7333, 3002, 5323, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
