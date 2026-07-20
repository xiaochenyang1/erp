CREATE TABLE IF NOT EXISTS sys_refresh_token (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    issued_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    replaced_by_token_hash VARCHAR(128),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_sys_refresh_token_hash ON sys_refresh_token (token_hash);
CREATE INDEX idx_sys_refresh_token_user_status ON sys_refresh_token (user_id, status);
CREATE INDEX idx_sys_refresh_token_expires_at ON sys_refresh_token (expires_at);

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag, status, deleted_flag, created_by, updated_by, version)
VALUES
    (5026, 5002, 'BUTTON', 'SYSTEM_USER_RESET_PASSWORD', '重置用户密码', NULL, NULL, 'system:user:reset-password', 10, 1, 'ACTIVE', 0, 0, 0, 0)
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
    (7111, 3002, 5026, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
