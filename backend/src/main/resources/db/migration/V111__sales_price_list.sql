-- V111: 销售价目（商品通用价 + 客户专价）+ 最低价校验支撑。
-- 取价优先级：客户+商品专价 > 商品通用价(customer_id IS NULL) > 无价目则不校验。
-- 号段：menu 5360-5361；role_menu 7370-7371（绑定 ERP_ADMIN=3002）。

CREATE TABLE IF NOT EXISTS md_sales_price (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    customer_id BIGINT NULL COMMENT '空=商品通用价；非空=客户专价',
    product_id BIGINT NOT NULL,
    list_price DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT '标准报价（含税单价参考）',
    min_price DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT '最低成交单价，下单不得低于此价',
    effective_from DATE NOT NULL,
    effective_to DATE NULL COMMENT '空=长期有效',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_md_sales_price_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_md_sales_price_company_book_product
    ON md_sales_price (company_id, account_book_id, product_id, status, deleted_flag);
CREATE INDEX idx_md_sales_price_company_book_customer_product
    ON md_sales_price (company_id, account_book_id, customer_id, product_id, status, deleted_flag);
CREATE INDEX idx_md_sales_price_effective
    ON md_sales_price (company_id, account_book_id, effective_from, effective_to);

-- 菜单挂销售 CATALOG(5139)
INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5360, 5139, 'MENU', 'SALES_PRICE', '销售价目', '/sales/prices',
     'sales/prices/index', 'sales:price:view', 4, 1, 'ACTIVE', 0, 0, 0, 0),
    (5361, 5360, 'BUTTON', 'SALES_PRICE_MANAGE', '维护销售价目', NULL, NULL,
     'sales:price:manage', 1, 1, 'ACTIVE', 0, 0, 0, 0)
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
    (7370, 3002, 5360, 0),
    (7371, 3002, 5361, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
