CREATE TABLE IF NOT EXISTS prd_issue (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    issue_no VARCHAR(64) NOT NULL,
    order_id BIGINT NOT NULL,
    issue_date DATE NOT NULL,
    total_qty DECIMAL(18, 4) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS prd_issue_line (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    issue_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    order_material_id BIGINT NOT NULL,
    material_product_id BIGINT NOT NULL,
    issue_qty DECIMAL(18, 4) NOT NULL,
    issue_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS prd_completion (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    completion_no VARCHAR(64) NOT NULL,
    order_id BIGINT NOT NULL,
    completion_date DATE NOT NULL,
    completed_qty DECIMAL(18, 4) NOT NULL,
    completed_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS prd_return (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    return_no VARCHAR(64) NOT NULL,
    order_id BIGINT NOT NULL,
    return_date DATE NOT NULL,
    total_qty DECIMAL(18, 4) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS prd_return_line (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    return_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    order_material_id BIGINT NOT NULL,
    material_product_id BIGINT NOT NULL,
    return_qty DECIMAL(18, 4) NOT NULL,
    return_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_prd_issue_company_no ON prd_issue (company_id, issue_no);
CREATE INDEX idx_prd_issue_company_order ON prd_issue (company_id, order_id, issue_date);
CREATE INDEX idx_prd_issue_line_company_issue ON prd_issue_line (company_id, issue_id);
CREATE INDEX idx_prd_issue_line_company_material ON prd_issue_line (company_id, order_material_id);
CREATE UNIQUE INDEX uk_prd_completion_company_no ON prd_completion (company_id, completion_no);
CREATE INDEX idx_prd_completion_company_order ON prd_completion (company_id, order_id, completion_date);
CREATE UNIQUE INDEX uk_prd_return_company_no ON prd_return (company_id, return_no);
CREATE INDEX idx_prd_return_company_order ON prd_return (company_id, order_id, return_date);
CREATE INDEX idx_prd_return_line_company_return ON prd_return_line (company_id, return_id);
CREATE INDEX idx_prd_return_line_company_material ON prd_return_line (company_id, order_material_id);

INSERT INTO sys_sequence_rule
(id, biz_type, prefix, date_pattern, seq_length, current_value, status, created_by, updated_by, version)
VALUES
    (2022, 'PRODUCTION_ISSUE', 'PI', 'yyyyMMdd', 4, 1, 'ACTIVE', 0, 0, 0),
    (2023, 'PRODUCTION_COMPLETION', 'PC', 'yyyyMMdd', 4, 1, 'ACTIVE', 0, 0, 0),
    (2024, 'PRODUCTION_RETURN', 'PR', 'yyyyMMdd', 4, 1, 'ACTIVE', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    prefix = VALUES(prefix),
    date_pattern = VALUES(date_pattern),
    seq_length = VALUES(seq_length),
    current_value = GREATEST(current_value, VALUES(current_value)),
    status = VALUES(status);

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5090, 5083, 'BUTTON', 'PRODUCTION_ORDER_RETURN', '生产退料', NULL, NULL,
     'production:order:return', 7, 1, 'ACTIVE', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_type = VALUES(menu_type),
    menu_name = VALUES(menu_name),
    permission = VALUES(permission),
    sort_no = VALUES(sort_no),
    visible_flag = VALUES(visible_flag),
    status = VALUES(status),
    deleted_flag = VALUES(deleted_flag),
    updated_by = VALUES(updated_by);

INSERT INTO sys_role_menu
(id, role_id, menu_id, created_by)
VALUES
    (7110, 3002, 5090, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
