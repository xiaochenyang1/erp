-- V131: 库存余额/流水/批次余额接入库位（兼容默认 MAIN）。
-- 历史数据回填仓库默认库位；过账未指定库位时使用默认库位。

ALTER TABLE inv_balance
    ADD COLUMN location_id BIGINT NULL;

ALTER TABLE inv_txn
    ADD COLUMN location_id BIGINT NULL;

ALTER TABLE inv_lot_balance
    ADD COLUMN location_id BIGINT NULL;

-- 回填默认库位（使用相关子查询，兼容 H2/MySQL）
UPDATE inv_balance b
SET b.location_id = (
    SELECT l.id
    FROM md_location l
    WHERE l.company_id = b.company_id
      AND l.account_book_id = b.account_book_id
      AND l.warehouse_id = b.warehouse_id
      AND l.is_default = 1
      AND l.deleted_flag = 0
    ORDER BY l.id
    LIMIT 1
)
WHERE b.location_id IS NULL;

UPDATE inv_txn t
SET t.location_id = (
    SELECT l.id
    FROM md_location l
    WHERE l.company_id = t.company_id
      AND l.account_book_id = t.account_book_id
      AND l.warehouse_id = t.warehouse_id
      AND l.is_default = 1
      AND l.deleted_flag = 0
    ORDER BY l.id
    LIMIT 1
)
WHERE t.location_id IS NULL;

UPDATE inv_lot_balance lb
SET lb.location_id = (
    SELECT l.id
    FROM md_location l
    WHERE l.company_id = lb.company_id
      AND l.account_book_id = lb.account_book_id
      AND l.warehouse_id = lb.warehouse_id
      AND l.is_default = 1
      AND l.deleted_flag = 0
    ORDER BY l.id
    LIMIT 1
)
WHERE lb.location_id IS NULL;

UPDATE inv_balance b
SET b.location_id = (
    SELECT l.id
    FROM md_location l
    WHERE l.company_id = b.company_id
      AND l.account_book_id = b.account_book_id
      AND l.warehouse_id = b.warehouse_id
      AND l.location_code = 'MAIN'
      AND l.deleted_flag = 0
    ORDER BY l.id
    LIMIT 1
)
WHERE b.location_id IS NULL;

UPDATE inv_txn t
SET t.location_id = (
    SELECT l.id
    FROM md_location l
    WHERE l.company_id = t.company_id
      AND l.account_book_id = t.account_book_id
      AND l.warehouse_id = t.warehouse_id
      AND l.location_code = 'MAIN'
      AND l.deleted_flag = 0
    ORDER BY l.id
    LIMIT 1
)
WHERE t.location_id IS NULL;

UPDATE inv_lot_balance lb
SET lb.location_id = (
    SELECT l.id
    FROM md_location l
    WHERE l.company_id = lb.company_id
      AND l.account_book_id = lb.account_book_id
      AND l.warehouse_id = lb.warehouse_id
      AND l.location_code = 'MAIN'
      AND l.deleted_flag = 0
    ORDER BY l.id
    LIMIT 1
)
WHERE lb.location_id IS NULL;

-- 重建余额唯一键（仓+库位+商品）
DROP INDEX uk_inv_balance_company_book_warehouse_product ON inv_balance;
CREATE UNIQUE INDEX uk_inv_balance_company_book_warehouse_location_product
    ON inv_balance (company_id, account_book_id, warehouse_id, location_id, product_id);
CREATE INDEX idx_inv_balance_company_book_location
    ON inv_balance (company_id, account_book_id, location_id);

DROP INDEX uk_inv_lot_balance_company_book_wh_product_lot ON inv_lot_balance;
CREATE UNIQUE INDEX uk_inv_lot_balance_company_book_wh_location_product_lot
    ON inv_lot_balance (company_id, account_book_id, warehouse_id, location_id, product_id, lot_no);
CREATE INDEX idx_inv_lot_balance_company_book_location
    ON inv_lot_balance (company_id, account_book_id, location_id);

CREATE INDEX idx_inv_txn_company_book_location
    ON inv_txn (company_id, account_book_id, location_id);
