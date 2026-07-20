CREATE TABLE IF NOT EXISTS sys_import_job (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    import_type VARCHAR(64) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_rows INT NOT NULL DEFAULT 0,
    valid_rows INT NOT NULL DEFAULT 0,
    error_rows INT NOT NULL DEFAULT 0,
    committed_rows INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_import_job_row (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    job_id BIGINT NOT NULL,
    row_no INT NOT NULL,
    raw_json TEXT NOT NULL,
    normalized_json TEXT NOT NULL,
    valid_flag TINYINT NOT NULL DEFAULT 0,
    error_json TEXT NOT NULL,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sys_import_job_company_type_status
    ON sys_import_job (company_id, import_type, status, created_time);
CREATE INDEX idx_sys_import_job_company_created
    ON sys_import_job (company_id, created_time);
CREATE UNIQUE INDEX uk_sys_import_job_row_job_row
    ON sys_import_job_row (job_id, row_no);
CREATE INDEX idx_sys_import_job_row_company_job
    ON sys_import_job_row (company_id, job_id);

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5070, 0, 'CATALOG', 'IMPORT_CENTER', '导入中心', '/imports', 'Layout', NULL, 11, 1, 'ACTIVE', 0, 0, 0, 0),
    (5071, 5070, 'MENU', 'INITIAL_IMPORT', '期初数据导入', '/imports/initial',
     'imports/initial/index', 'import:init:manage', 1, 1, 'ACTIVE', 0, 0, 0, 0)
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
    (7090, 3002, 5070, 0),
    (7091, 3002, 5071, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
