CREATE TABLE IF NOT EXISTS inv_reservation_event (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    reservation_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id BIGINT NOT NULL,
    source_no VARCHAR(64) NOT NULL,
    source_line_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    event_qty DECIMAL(18, 4) NOT NULL,
    remaining_qty_before DECIMAL(18, 4) NOT NULL,
    remaining_qty_after DECIMAL(18, 4) NOT NULL,
    reason VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_inv_reservation_event_company_reservation
    ON inv_reservation_event (company_id, reservation_id, created_time);
CREATE INDEX idx_inv_reservation_event_company_source
    ON inv_reservation_event (company_id, source_type, source_id);
CREATE INDEX idx_inv_reservation_event_company_balance
    ON inv_reservation_event (company_id, warehouse_id, product_id, created_time);

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5016, 5009, 'MENU', 'INVENTORY_RESERVATION', '库存预占', '/inventory/reservations',
     'inventory/reservation/index', 'inventory:reservation:view', 5, 1, 'ACTIVE', 0, 0, 0, 0),
    (5017, 5016, 'BUTTON', 'INVENTORY_RESERVATION_CHECK', '库存预占巡检', NULL, NULL,
     'inventory:reservation:check', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5018, 5016, 'BUTTON', 'INVENTORY_RESERVATION_RELEASE', '释放库存预占', NULL, NULL,
     'inventory:reservation:release', 2, 1, 'ACTIVE', 0, 0, 0, 0)
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
