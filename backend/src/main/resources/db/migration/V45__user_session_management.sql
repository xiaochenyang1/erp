ALTER TABLE sys_refresh_token ADD COLUMN company_id BIGINT;
ALTER TABLE sys_refresh_token ADD COLUMN account_book_id BIGINT;
ALTER TABLE sys_refresh_token ADD COLUMN login_ip VARCHAR(64);
ALTER TABLE sys_refresh_token ADD COLUMN user_agent VARCHAR(512);
ALTER TABLE sys_refresh_token ADD COLUMN last_used_at TIMESTAMP;

UPDATE sys_refresh_token rt
SET rt.company_id = (SELECT u.company_id FROM sys_user u WHERE u.id = rt.user_id),
    rt.account_book_id = (SELECT u.account_book_id FROM sys_user u WHERE u.id = rt.user_id),
    rt.last_used_at = rt.issued_at
WHERE rt.company_id IS NULL
  AND EXISTS (SELECT 1 FROM sys_user u WHERE u.id = rt.user_id);

CREATE INDEX idx_sys_refresh_token_company_status ON sys_refresh_token (company_id, status);
CREATE INDEX idx_sys_refresh_token_account_book ON sys_refresh_token (account_book_id);

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag, status, deleted_flag, created_by, updated_by, version)
VALUES
    (5027, 5001, 'MENU', 'SYSTEM_USER_SESSION', '在线会话', '/system/user-sessions', 'system/user-session/index', 'system:user-session:view', 8, 1, 'ACTIVE', 0, 0, 0, 0),
    (5028, 5027, 'BUTTON', 'SYSTEM_USER_SESSION_REVOKE', '撤销会话', NULL, NULL, 'system:user-session:revoke', 1, 1, 'ACTIVE', 0, 0, 0, 0)
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
    deleted_flag = VALUES(deleted_flag);

INSERT INTO sys_role_menu
(id, role_id, menu_id, created_by)
VALUES
    (7112, 3002, 5027, 0),
    (7113, 3002, 5028, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
