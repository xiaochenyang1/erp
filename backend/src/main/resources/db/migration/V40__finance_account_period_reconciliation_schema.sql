CREATE TABLE IF NOT EXISTS fin_account_period (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    period_year INT NOT NULL,
    period_month VARCHAR(7) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'LOCKED', 'CLOSED')),
    locked_by BIGINT,
    locked_time TIMESTAMP,
    closed_by BIGINT,
    closed_time TIMESTAMP,
    reopened_by BIGINT,
    reopened_time TIMESTAMP,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_fin_account_period_book_month
    ON fin_account_period (company_id, account_book_id, period_month);
CREATE INDEX idx_fin_account_period_book_status_month
    ON fin_account_period (company_id, account_book_id, status, period_month);

INSERT INTO fin_account_subject
(id, company_id, account_book_id, subject_code, subject_name, parent_id, subject_type, balance_direction,
 status, deleted_flag, remark, created_by, updated_by, version)
VALUES
    (910008, 1, 1, '6402', '主营业务成本', NULL, 'EXPENSE', 'DEBIT', 'ACTIVE', 0, '销售出库成本结转科目', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    subject_name = VALUES(subject_name),
    subject_type = VALUES(subject_type),
    balance_direction = VALUES(balance_direction),
    status = VALUES(status),
    deleted_flag = VALUES(deleted_flag),
    remark = VALUES(remark),
    updated_by = VALUES(updated_by);

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5037, 5030, 'MENU', 'FINANCE_PERIOD', '期间锁账', '/finance/periods',
     'finance/period/index', 'finance:period:view', 7, 1, 'ACTIVE', 0, 0, 0, 0),
    (5038, 5037, 'BUTTON', 'FINANCE_PERIOD_MANAGE', '生成期间', NULL,
     NULL, 'finance:period:manage', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5039, 5037, 'BUTTON', 'FINANCE_PERIOD_CLOSE', '锁定结账', NULL,
     NULL, 'finance:period:close', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5040, 5037, 'BUTTON', 'FINANCE_PERIOD_REOPEN', '反开期间', NULL,
     NULL, 'finance:period:reopen', 3, 1, 'ACTIVE', 0, 0, 0, 0)
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
    (7057, 3002, 5037, 0),
    (7058, 3002, 5038, 0),
    (7059, 3002, 5039, 0),
    (7060, 3002, 5040, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
