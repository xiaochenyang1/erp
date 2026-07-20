CREATE TABLE IF NOT EXISTS prd_completion_reversal (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    reversal_no VARCHAR(64) NOT NULL,
    order_id BIGINT NOT NULL,
    reversal_date DATE NOT NULL,
    reversed_qty DECIMAL(18, 4) NOT NULL,
    reversed_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_prd_completion_reversal_company_no ON prd_completion_reversal (company_id, reversal_no);
CREATE INDEX idx_prd_completion_reversal_company_order ON prd_completion_reversal (company_id, order_id, reversal_date);

INSERT INTO sys_sequence_rule
(id, company_id, biz_type, prefix, date_pattern, seq_length, current_value, status, created_by, updated_by, version)
VALUES
    (2025, 1, 'PRODUCTION_COMPLETION_REVERSAL', 'PCR', 'yyyyMMdd', 4, 1, 'ACTIVE', 0, 0, 0)
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
    (5094, 5083, 'BUTTON', 'PRODUCTION_ORDER_REVERSE_COMPLETION', '生产反完工', NULL, NULL,
     'production:order:reverse-completion', 8, 1, 'ACTIVE', 0, 0, 0, 0)
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
    (7132, 3002, 5094, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
