ALTER TABLE inv_balance
    ADD COLUMN qty_reserved DECIMAL(18, 4) NOT NULL DEFAULT 0;

ALTER TABLE sal_order
    ADD COLUMN warehouse_id BIGINT;

UPDATE sal_order
SET warehouse_id = (
    SELECT MIN(sal_delivery.warehouse_id)
    FROM sal_delivery
    WHERE sal_delivery.order_id = sal_order.id
      AND sal_delivery.company_id = sal_order.company_id
)
WHERE warehouse_id IS NULL
  AND EXISTS (
      SELECT 1
      FROM sal_delivery
      WHERE sal_delivery.order_id = sal_order.id
        AND sal_delivery.company_id = sal_order.company_id
  );

UPDATE sal_order
SET warehouse_id = (
    SELECT MIN(md_warehouse.id)
    FROM md_warehouse
    WHERE md_warehouse.company_id = sal_order.company_id
      AND md_warehouse.deleted_flag = 0
)
WHERE warehouse_id IS NULL
  AND EXISTS (
      SELECT 1
      FROM md_warehouse
      WHERE md_warehouse.company_id = sal_order.company_id
        AND md_warehouse.deleted_flag = 0
  );

ALTER TABLE sal_order
    MODIFY COLUMN warehouse_id BIGINT NOT NULL;

CREATE TABLE IF NOT EXISTS inv_reservation (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    warehouse_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id BIGINT NOT NULL,
    source_no VARCHAR(64) NOT NULL,
    source_line_id BIGINT NOT NULL,
    reserved_qty DECIMAL(18, 4) NOT NULL,
    released_qty DECIMAL(18, 4) NOT NULL DEFAULT 0,
    remaining_qty DECIMAL(18, 4) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_sal_order_company_warehouse_id ON sal_order (company_id, warehouse_id);
CREATE UNIQUE INDEX uk_inv_reservation_company_source_line ON inv_reservation (company_id, source_type, source_line_id);
CREATE INDEX idx_inv_reservation_company_source ON inv_reservation (company_id, source_type, source_id);
CREATE INDEX idx_inv_reservation_company_balance ON inv_reservation (company_id, warehouse_id, product_id, status);
