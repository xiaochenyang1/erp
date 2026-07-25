-- V129: MRP 可执行计划持久化 + 转单权限。
-- 号段：menu 5440；role_menu 7450（绑定 ERP_ADMIN=3002）。

CREATE TABLE IF NOT EXISTS inv_mrp_run (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    run_no VARCHAR(64) NOT NULL,
    as_of_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    purchase_count INT NOT NULL DEFAULT 0,
    production_count INT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_inv_mrp_run_status CHECK (status IN ('OPEN', 'CLOSED', 'CANCELLED'))
);

CREATE UNIQUE INDEX uk_inv_mrp_run_company_book_run_no
    ON inv_mrp_run (company_id, account_book_id, run_no);
CREATE INDEX idx_inv_mrp_run_company_book_status
    ON inv_mrp_run (company_id, account_book_id, status, deleted_flag, created_time);

CREATE TABLE IF NOT EXISTS inv_mrp_run_line (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    run_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    product_id BIGINT NOT NULL,
    suggestion_type VARCHAR(16) NOT NULL,
    demand_qty DECIMAL(18, 4) NOT NULL DEFAULT 0,
    on_hand_qty DECIMAL(18, 4) NOT NULL DEFAULT 0,
    open_supply_qty DECIMAL(18, 4) NOT NULL DEFAULT 0,
    net_qty DECIMAL(18, 4) NOT NULL DEFAULT 0,
    bom_id BIGINT NULL,
    reason VARCHAR(512),
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    converted_biz_type VARCHAR(32) NULL,
    converted_biz_id BIGINT NULL,
    converted_biz_no VARCHAR(64) NULL,
    converted_time TIMESTAMP NULL,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_inv_mrp_run_line_type CHECK (suggestion_type IN ('PURCHASE', 'PRODUCTION')),
    CONSTRAINT chk_inv_mrp_run_line_status CHECK (status IN ('OPEN', 'CONVERTED', 'CANCELLED'))
);

CREATE INDEX idx_inv_mrp_run_line_run
    ON inv_mrp_run_line (company_id, account_book_id, run_id, deleted_flag, line_no);
CREATE INDEX idx_inv_mrp_run_line_status
    ON inv_mrp_run_line (company_id, account_book_id, status, suggestion_type, deleted_flag);

INSERT INTO sys_sequence_rule
(id, company_id, account_book_id, biz_type, prefix, date_pattern, seq_length, current_value,
 status, created_by, updated_by, version)
VALUES
    (2034, 1, 1, 'MRP_RUN', 'MRP', 'yyyyMMdd', 4, 0, 'ACTIVE', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    company_id = VALUES(company_id),
    account_book_id = VALUES(account_book_id),
    prefix = VALUES(prefix),
    current_value = GREATEST(COALESCE(current_value, 0), VALUES(current_value)),
    status = VALUES(status);

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5440, 5410, 'BUTTON', 'INVENTORY_MRP_CONVERT', 'MRP转单', NULL, NULL,
     'inventory:mrp:convert', 2, 1, 'ACTIVE', 0, 0, 0, 0)
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
    (7450, 3002, 5440, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
