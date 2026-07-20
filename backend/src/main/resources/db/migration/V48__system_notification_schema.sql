CREATE TABLE IF NOT EXISTS sys_notification (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    category VARCHAR(32) NOT NULL,
    notification_type VARCHAR(64) NOT NULL,
    title VARCHAR(128) NOT NULL,
    content VARCHAR(512),
    business_type VARCHAR(64),
    business_id BIGINT,
    business_no VARCHAR(128),
    target_url VARCHAR(512),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_notification_recipient (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    notification_id BIGINT NOT NULL,
    recipient_user_id BIGINT NOT NULL,
    read_flag TINYINT NOT NULL DEFAULT 0,
    read_time TIMESTAMP,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_sys_notification_business
    ON sys_notification (company_id, account_book_id, business_type, business_id, category, notification_type);
CREATE INDEX idx_sys_notification_created_time
    ON sys_notification (company_id, account_book_id, created_time);
CREATE INDEX idx_sys_notification_recipient_user
    ON sys_notification_recipient (company_id, recipient_user_id, status, read_flag, created_time);
CREATE INDEX idx_sys_notification_recipient_notification
    ON sys_notification_recipient (company_id, notification_id, status);

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag, status, deleted_flag, created_by, updated_by, version)
VALUES
    (5063, 5001, 'MENU', 'SYSTEM_NOTIFICATION', '通知中心', '/system/notifications', 'system/notification/index', 'system:notification:view', 10, 1, 'ACTIVE', 0, 0, 0, 0)
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
    (7123, 3002, 5063, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
