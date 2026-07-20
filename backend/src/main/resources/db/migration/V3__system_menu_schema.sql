CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT PRIMARY KEY,
    parent_id BIGINT NOT NULL DEFAULT 0,
    menu_type VARCHAR(32) NOT NULL,
    menu_code VARCHAR(64) NOT NULL,
    menu_name VARCHAR(64) NOT NULL,
    path VARCHAR(255),
    component VARCHAR(255),
    permission VARCHAR(128),
    sort_no INT NOT NULL DEFAULT 0,
    visible_flag TINYINT NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_role_menu (
    id BIGINT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_sys_menu_menu_code ON sys_menu (menu_code);
CREATE UNIQUE INDEX uk_sys_role_menu_role_id_menu_id ON sys_role_menu (role_id, menu_id);
CREATE INDEX idx_sys_menu_parent_id ON sys_menu (parent_id);
CREATE INDEX idx_sys_role_menu_role_id ON sys_role_menu (role_id);
CREATE INDEX idx_sys_role_menu_menu_id ON sys_role_menu (menu_id);
