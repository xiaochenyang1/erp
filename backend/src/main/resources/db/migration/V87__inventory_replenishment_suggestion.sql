CREATE TABLE IF NOT EXISTS inv_replenishment_suggestion (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    suggestion_no VARCHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_rule_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    supplier_id BIGINT,
    suggested_qty DECIMAL(20, 4) NOT NULL,
    shortage_qty_snapshot DECIMAL(20, 4) NOT NULL DEFAULT 0,
    expected_arrival_date DATE,
    status VARCHAR(16) NOT NULL,
    purchase_order_id BIGINT,
    purchase_order_no VARCHAR(64),
    remark VARCHAR(512),
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_inv_replenishment_suggestion_status CHECK (status IN ('DRAFT', 'CONVERTED', 'CANCELLED'))
);

CREATE UNIQUE INDEX uk_inv_replenishment_suggestion_no
    ON inv_replenishment_suggestion (company_id, account_book_id, suggestion_no);

CREATE INDEX idx_inv_replenishment_suggestion_active_source
    ON inv_replenishment_suggestion (company_id, account_book_id, source_type, warehouse_id, product_id, status, deleted_flag);

CREATE INDEX idx_inv_replenishment_suggestion_query
    ON inv_replenishment_suggestion (company_id, account_book_id, status, warehouse_id, product_id, supplier_id, deleted_flag);

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5104, 505, 'MENU', 'INVENTORY_REPLENISHMENT', '补货建议', '/inventory/replenishment-suggestions',
     'inventory/replenishment-suggestions/index', 'inventory:replenishment:view', 6, 1, 'ACTIVE', 0, 0, 0, 0),
    (5105, 5104, 'BUTTON', 'INVENTORY_REPLENISHMENT_CREATE', '生成补货建议', NULL, NULL,
     'inventory:replenishment:create', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5106, 5104, 'BUTTON', 'INVENTORY_REPLENISHMENT_CANCEL', '取消补货建议', NULL, NULL,
     'inventory:replenishment:cancel', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5107, 5104, 'BUTTON', 'INVENTORY_REPLENISHMENT_CONVERT', '转采购订单', NULL, NULL,
     'inventory:replenishment:convert', 3, 1, 'ACTIVE', 0, 0, 0, 0)
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
