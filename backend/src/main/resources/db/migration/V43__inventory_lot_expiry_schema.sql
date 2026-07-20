ALTER TABLE md_product
    ADD COLUMN lot_controlled TINYINT NOT NULL DEFAULT 0;
ALTER TABLE md_product
    ADD COLUMN shelf_life_controlled TINYINT NOT NULL DEFAULT 0;

ALTER TABLE inv_txn
    ADD COLUMN lot_no VARCHAR(64);
ALTER TABLE inv_txn
    ADD COLUMN production_date DATE;
ALTER TABLE inv_txn
    ADD COLUMN expiry_date DATE;
ALTER TABLE inv_txn
    ADD COLUMN lot_key VARCHAR(80) NOT NULL DEFAULT '';

CREATE TABLE IF NOT EXISTS inv_lot_balance (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    warehouse_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    lot_no VARCHAR(64) NOT NULL,
    production_date DATE,
    expiry_date DATE,
    first_inbound_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    qty_on_hand DECIMAL(18, 4) NOT NULL DEFAULT 0,
    qty_reserved DECIMAL(18, 4) NOT NULL DEFAULT 0,
    amount_on_hand DECIMAL(18, 2) NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_inv_lot_balance_company_book_wh_product_lot
    ON inv_lot_balance (company_id, account_book_id, warehouse_id, product_id, lot_no);
CREATE INDEX idx_inv_lot_balance_company_book_product_expiry
    ON inv_lot_balance (company_id, account_book_id, product_id, expiry_date);
CREATE INDEX idx_inv_lot_balance_company_book_wh_product
    ON inv_lot_balance (company_id, account_book_id, warehouse_id, product_id);
CREATE INDEX idx_inv_lot_balance_company_book_pick
    ON inv_lot_balance (company_id, account_book_id, warehouse_id, product_id, expiry_date, first_inbound_time);

DROP INDEX uk_inv_txn_company_biz_line_direction ON inv_txn;
CREATE UNIQUE INDEX uk_inv_txn_company_biz_line_direction_lot_key
    ON inv_txn (company_id, biz_type, biz_line_id, direction, lot_key);
CREATE INDEX idx_inv_txn_company_biz_line_direction
    ON inv_txn (company_id, biz_type, biz_line_id, direction);
CREATE INDEX idx_inv_txn_company_lot
    ON inv_txn (company_id, warehouse_id, product_id, lot_no);

ALTER TABLE pur_receipt_line ADD COLUMN lot_no VARCHAR(64);
ALTER TABLE pur_receipt_line ADD COLUMN production_date DATE;
ALTER TABLE pur_receipt_line ADD COLUMN expiry_date DATE;

ALTER TABLE pur_return_line ADD COLUMN lot_no VARCHAR(64);
ALTER TABLE pur_return_line ADD COLUMN production_date DATE;
ALTER TABLE pur_return_line ADD COLUMN expiry_date DATE;

ALTER TABLE sal_delivery_line ADD COLUMN lot_no VARCHAR(64);
ALTER TABLE sal_delivery_line ADD COLUMN production_date DATE;
ALTER TABLE sal_delivery_line ADD COLUMN expiry_date DATE;

ALTER TABLE sal_return_line ADD COLUMN lot_no VARCHAR(64);
ALTER TABLE sal_return_line ADD COLUMN production_date DATE;
ALTER TABLE sal_return_line ADD COLUMN expiry_date DATE;

ALTER TABLE inv_adjustment_line ADD COLUMN lot_no VARCHAR(64);
ALTER TABLE inv_adjustment_line ADD COLUMN production_date DATE;
ALTER TABLE inv_adjustment_line ADD COLUMN expiry_date DATE;

ALTER TABLE inv_stock_check_line ADD COLUMN lot_no VARCHAR(64);
ALTER TABLE inv_stock_check_line ADD COLUMN production_date DATE;
ALTER TABLE inv_stock_check_line ADD COLUMN expiry_date DATE;

ALTER TABLE inv_transfer_line ADD COLUMN lot_no VARCHAR(64);
ALTER TABLE inv_transfer_line ADD COLUMN production_date DATE;
ALTER TABLE inv_transfer_line ADD COLUMN expiry_date DATE;

ALTER TABLE prd_issue_line ADD COLUMN lot_no VARCHAR(64);
ALTER TABLE prd_issue_line ADD COLUMN production_date DATE;
ALTER TABLE prd_issue_line ADD COLUMN expiry_date DATE;

ALTER TABLE prd_completion ADD COLUMN lot_no VARCHAR(64);
ALTER TABLE prd_completion ADD COLUMN production_date DATE;
ALTER TABLE prd_completion ADD COLUMN expiry_date DATE;

ALTER TABLE prd_return_line ADD COLUMN lot_no VARCHAR(64);
ALTER TABLE prd_return_line ADD COLUMN production_date DATE;
ALTER TABLE prd_return_line ADD COLUMN expiry_date DATE;
