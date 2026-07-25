-- V128: 采购价目（商品通用价 + 供应商专价）+ 最高价校验支撑。
-- 取价优先级：供应商+商品专价 > 商品通用价(supplier_id IS NULL) > 无价目则不校验。
-- 采购订单单价不得高于生效最高价（成本控制，对称于销售最低价）。
-- 号段：menu 5430-5431；role_menu 7440-7441（绑定 ERP_ADMIN=3002）。

CREATE TABLE IF NOT EXISTS md_purchase_price (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    supplier_id BIGINT NULL COMMENT '空=商品通用价；非空=供应商专价',
    product_id BIGINT NOT NULL,
    list_price DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT '标准采购价（含税单价参考）',
    max_price DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT '最高成交单价，下单不得高于此价',
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
    CONSTRAINT chk_md_purchase_price_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_md_purchase_price_company_book_product
    ON md_purchase_price (company_id, account_book_id, product_id, status, deleted_flag);
CREATE INDEX idx_md_purchase_price_company_book_supplier_product
    ON md_purchase_price (company_id, account_book_id, supplier_id, product_id, status, deleted_flag);
CREATE INDEX idx_md_purchase_price_effective
    ON md_purchase_price (company_id, account_book_id, effective_from, effective_to);

-- 菜单挂采购 CATALOG(5135)
INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5430, 5135, 'MENU', 'PURCHASE_PRICE', '采购价目', '/purchase/prices',
     'purchase/prices/index', 'purchase:price:view', 5, 1, 'ACTIVE', 0, 0, 0, 0),
    (5431, 5430, 'BUTTON', 'PURCHASE_PRICE_MANAGE', '维护采购价目', NULL, NULL,
     'purchase:price:manage', 1, 1, 'ACTIVE', 0, 0, 0, 0)
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
    (7440, 3002, 5430, 0),
    (7441, 3002, 5431, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
