-- V162: account-book scoped base currency and currency lifecycle menu.
CREATE TABLE IF NOT EXISTS md_account_book_currency (
    id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_md_account_book_currency_scope (company_id, account_book_id),
    KEY idx_md_account_book_currency_code (company_id, account_book_id, currency_code, status, deleted_flag)
);

INSERT INTO md_account_book_currency
(id, company_id, account_book_id, currency_code, status, deleted_flag, created_by, updated_by, version)
SELECT 921001, 1, 1, 'CNY', 'ENABLED', 0, 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM md_account_book_currency WHERE company_id = 1 AND account_book_id = 1
);

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5600, 5130, 'MENU', 'MASTERDATA_CURRENCY', '币种与汇率', '/finance/currencies',
     'finance/currencies/index', 'masterdata:currency:view', 6, 1, 'ACTIVE', 0, 0, 0, 0),
    (5601, 5600, 'BUTTON', 'MASTERDATA_CURRENCY_CREATE', '新增币种', NULL, NULL,
     'masterdata:currency:manage', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5602, 5600, 'BUTTON', 'MASTERDATA_CURRENCY_ENABLE', '启用币种', NULL, NULL,
     'masterdata:currency:manage', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5603, 5600, 'BUTTON', 'MASTERDATA_CURRENCY_DISABLE', '停用币种', NULL, NULL,
     'masterdata:currency:manage', 3, 1, 'ACTIVE', 0, 0, 0, 0),
    (5604, 5600, 'BUTTON', 'MASTERDATA_EXCHANGE_RATE_MANAGE', '维护汇率', NULL, NULL,
     'masterdata:currency:manage', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    (5605, 5600, 'BUTTON', 'MASTERDATA_BASE_CURRENCY_MANAGE', '设置本位币', NULL, NULL,
     'masterdata:currency:manage', 5, 1, 'ACTIVE', 0, 0, 0, 0)
;

INSERT INTO sys_role_menu (id, role_id, menu_id, created_by)
VALUES
    (7600, 3002, 5600, 0),
    (7601, 3002, 5601, 0),
    (7602, 3002, 5602, 0),
    (7603, 3002, 5603, 0),
    (7604, 3002, 5604, 0),
    (7605, 3002, 5605, 0)
;
