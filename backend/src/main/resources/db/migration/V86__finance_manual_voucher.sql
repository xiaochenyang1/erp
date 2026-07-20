-- 手工凭证：财务人员手工录入的记账凭证（计提、结转、更正等）。
-- 与业务单据自动生成的凭证（fin_voucher.source_type != 'MANUAL_*'）分离管理：
--   头表 fin_manual_voucher + 行表 fin_manual_voucher_line 承载草稿/审批阶段，
--   草稿分录不进 fin_voucher_entry，因此不污染总账/月结/对账；
--   过账（POST）时才把审批通过的分录灌入共享的 fin_voucher + fin_voucher_entry，
--   与自动凭证同源进总账，FinanceLedgerService 无需改动。
-- 状态机：DRAFT →(提交)→ PENDING →(审批)→ APPROVED →(过账)→ POSTED；
--          PENDING →(驳回)→ DRAFT；POSTED →(作废)→ CANCELLED（删除已写入的 entry 分录）。
CREATE TABLE IF NOT EXISTS fin_manual_voucher (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    voucher_no VARCHAR(64) NOT NULL,
    biz_date DATE NOT NULL,
    amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    remark VARCHAR(512),
    -- 过账后回填共享凭证 id，供作废时定位 fin_voucher / fin_voucher_entry
    posted_voucher_id BIGINT,
    submitted_by BIGINT,
    submitted_time TIMESTAMP NULL,
    approved_by BIGINT,
    approved_time TIMESTAMP NULL,
    posted_by BIGINT,
    posted_time TIMESTAMP NULL,
    cancelled_by BIGINT,
    cancelled_time TIMESTAMP NULL,
    reject_reason VARCHAR(512),
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_fin_manual_voucher_status
        CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'POSTED', 'CANCELLED'))
);

CREATE TABLE IF NOT EXISTS fin_manual_voucher_line (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    voucher_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    subject_id BIGINT NOT NULL,
    subject_code VARCHAR(64) NOT NULL,
    subject_name VARCHAR(128) NOT NULL,
    debit_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    credit_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    summary VARCHAR(255),
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_fin_manual_voucher_company_no
    ON fin_manual_voucher (company_id, account_book_id, voucher_no);
CREATE INDEX idx_fin_manual_voucher_status_date
    ON fin_manual_voucher (company_id, account_book_id, status, biz_date);
CREATE UNIQUE INDEX uk_fin_manual_voucher_line_voucher_line
    ON fin_manual_voucher_line (voucher_id, line_no);
CREATE INDEX idx_fin_manual_voucher_line_voucher
    ON fin_manual_voucher_line (company_id, account_book_id, voucher_id);

-- 手工凭证号规则：MV + yyyyMMdd + 4 位序号
INSERT INTO sys_sequence_rule
(id, biz_type, prefix, date_pattern, seq_length, current_value, status, created_by, updated_by, version)
VALUES
    (2013, 'FIN_MANUAL_VOUCHER', 'MV', 'yyyyMMdd', 4, 0, 'ACTIVE', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    prefix = VALUES(prefix),
    date_pattern = VALUES(date_pattern),
    seq_length = VALUES(seq_length),
    current_value = GREATEST(current_value, VALUES(current_value)),
    status = VALUES(status);

-- 手工凭证操作按钮权限，挂在既有"记账凭证"菜单 5033 下。
-- SUPER_ADMIN 通过 PermissionCodes.allPermissions() 自动获得，此处种子供普通角色菜单树下发。
INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5104, 5033, 'BUTTON', 'FINANCE_VOUCHER_MANAGE', '录入手工凭证', NULL, NULL,
     'finance:voucher:manage', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5105, 5033, 'BUTTON', 'FINANCE_VOUCHER_APPROVE', '审批手工凭证', NULL, NULL,
     'finance:voucher:approve', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5106, 5033, 'BUTTON', 'FINANCE_VOUCHER_POST', '过账手工凭证', NULL, NULL,
     'finance:voucher:post', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5107, 5030, 'MENU', 'FINANCE_MANUAL_VOUCHER', '手工凭证', '/finance/vouchers/manual',
     'finance/vouchers/manual/index', 'finance:voucher:view', 4, 1, 'ACTIVE', 0, 0, 0, 0)
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
    (7148, 3002, 5104, 0),
    (7149, 3002, 5105, 0),
    (7150, 3002, 5106, 0),
    (7151, 3002, 5107, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
