-- V146: 年/月预算、部门/科目维度与执行控制
CREATE TABLE IF NOT EXISTS fin_budget (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    budget_year INT NOT NULL,
    budget_name VARCHAR(128) NOT NULL,
    control_policy VARCHAR(32) NOT NULL DEFAULT 'REJECT'
        CHECK (control_policy IN ('REJECT', 'APPROVAL')),
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'CLOSED', 'CANCELLED')),
    remark VARCHAR(255),
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS fin_budget_line (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    budget_id BIGINT NOT NULL,
    period_month INT NOT NULL CHECK (period_month BETWEEN 0 AND 12),
    dept_id BIGINT,
    subject_id BIGINT NOT NULL,
    budget_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    committed_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    actual_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_fin_budget_company_year_name
    ON fin_budget (company_id, account_book_id, budget_year, budget_name);
CREATE INDEX idx_fin_budget_company_year_status
    ON fin_budget (company_id, account_book_id, budget_year, status);
CREATE INDEX idx_fin_budget_line_budget_period
    ON fin_budget_line (company_id, account_book_id, budget_id, period_month);
CREATE INDEX idx_fin_budget_line_scope
    ON fin_budget_line (company_id, account_book_id, subject_id, dept_id, period_month);
CREATE UNIQUE INDEX uk_fin_budget_line_company_book_budget_scope
    ON fin_budget_line (company_id, account_book_id, budget_id, period_month, dept_id, subject_id);

ALTER TABLE fin_expense ADD COLUMN dept_id BIGINT AFTER expense_date;
ALTER TABLE fin_expense ADD COLUMN budget_line_id BIGINT AFTER amount;
ALTER TABLE fin_expense ADD COLUMN budget_state VARCHAR(32) NOT NULL DEFAULT 'NONE' AFTER budget_line_id;
ALTER TABLE fin_expense ADD COLUMN budget_overrun_flag TINYINT NOT NULL DEFAULT 0 AFTER budget_state;
CREATE INDEX idx_fin_expense_company_book_dept_subject_date
    ON fin_expense (company_id, account_book_id, dept_id, subject_id, expense_date);

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5490, 5030, 'MENU', 'FINANCE_BUDGET', '预算管理', '/finance/budgets',
     'finance/budgets/index', 'finance:budget:view', 5, 1, 'ACTIVE', 0, 0, 0, 0),
    (5491, 5490, 'BUTTON', 'FINANCE_BUDGET_MANAGE', '预算维护', NULL,
     NULL, 'finance:budget:manage', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5492, 5490, 'BUTTON', 'FINANCE_BUDGET_APPROVE', '预算审批', NULL,
     NULL, 'finance:budget:approve', 2, 1, 'ACTIVE', 0, 0, 0, 0) AS new
ON DUPLICATE KEY UPDATE
    parent_id = new.parent_id,
    menu_type = new.menu_type,
    menu_name = new.menu_name,
    path = new.path,
    component = new.component,
    permission = new.permission,
    sort_no = new.sort_no,
    visible_flag = new.visible_flag,
    status = new.status,
    deleted_flag = new.deleted_flag,
    updated_by = new.updated_by;

INSERT INTO sys_role_menu (id, role_id, menu_id, created_by)
VALUES
    (7500, 3002, 5490, 0),
    (7501, 3002, 5491, 0),
    (7502, 3002, 5492, 0) AS new
ON DUPLICATE KEY UPDATE
    role_id = new.role_id,
    menu_id = new.menu_id;
