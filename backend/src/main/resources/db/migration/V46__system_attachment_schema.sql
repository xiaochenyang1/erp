CREATE TABLE IF NOT EXISTS sys_attachment (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    business_type VARCHAR(64) NOT NULL,
    business_id BIGINT NOT NULL,
    business_no VARCHAR(128),
    original_filename VARCHAR(255) NOT NULL,
    storage_path VARCHAR(512) NOT NULL,
    content_type VARCHAR(128),
    file_size BIGINT NOT NULL,
    checksum_sha256 VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_sys_attachment_business ON sys_attachment (company_id, account_book_id, business_type, business_id, deleted_flag);
CREATE INDEX idx_sys_attachment_created_time ON sys_attachment (company_id, created_time);

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag, status, deleted_flag, created_by, updated_by, version)
VALUES
    (5060, 5001, 'MENU', 'SYSTEM_ATTACHMENT', '附件中心', '/system/attachments', 'system/attachment/index', 'system:attachment:view', 9, 1, 'ACTIVE', 0, 0, 0, 0),
    (5061, 5060, 'BUTTON', 'SYSTEM_ATTACHMENT_MANAGE', '上传附件', NULL, NULL, 'system:attachment:manage', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5062, 5060, 'BUTTON', 'SYSTEM_ATTACHMENT_DELETE', '删除附件', NULL, NULL, 'system:attachment:delete', 2, 1, 'ACTIVE', 0, 0, 0, 0)
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
    (7120, 3002, 5060, 0),
    (7121, 3002, 5061, 0),
    (7122, 3002, 5062, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
