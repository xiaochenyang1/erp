-- V132: 序列号轻量管理。
-- 商品可启用序列号控制；库存序列台账记录入库/出库状态。
-- 号段：menu 5460-5461；role_menu 7470-7471。

ALTER TABLE md_product
    ADD COLUMN serial_controlled TINYINT NOT NULL DEFAULT 0 AFTER inspection_required;

CREATE TABLE IF NOT EXISTS inv_serial_number (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    warehouse_id BIGINT NULL,
    location_id BIGINT NULL,
    serial_no VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'IN_STOCK',
    inbound_biz_type VARCHAR(32) NULL,
    inbound_biz_no VARCHAR(64) NULL,
    outbound_biz_type VARCHAR(32) NULL,
    outbound_biz_no VARCHAR(64) NULL,
    remark VARCHAR(255),
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_inv_serial_status CHECK (status IN ('IN_STOCK', 'ISSUED', 'SCRAPPED'))
);

CREATE UNIQUE INDEX uk_inv_serial_company_book_product_serial
    ON inv_serial_number (company_id, account_book_id, product_id, serial_no);
CREATE INDEX idx_inv_serial_company_book_status
    ON inv_serial_number (company_id, account_book_id, status, deleted_flag);
CREATE INDEX idx_inv_serial_company_book_warehouse
    ON inv_serial_number (company_id, account_book_id, warehouse_id, product_id, status);

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5460, 5009, 'MENU', 'INVENTORY_SERIAL', '序列号台账', '/inventory/serials',
     'inventory/serials/index', 'inventory:serial:view', 25, 1, 'ACTIVE', 0, 0, 0, 0),
    (5461, 5460, 'BUTTON', 'INVENTORY_SERIAL_MANAGE', '维护序列号', NULL, NULL,
     'inventory:serial:manage', 1, 1, 'ACTIVE', 0, 0, 0, 0)
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
    (7470, 3002, 5460, 0),
    (7471, 3002, 5461, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
