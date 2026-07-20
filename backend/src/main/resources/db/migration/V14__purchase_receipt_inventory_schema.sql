ALTER TABLE pur_order
    ADD COLUMN receipt_status VARCHAR(32) NOT NULL DEFAULT 'NOT_RECEIVED';

ALTER TABLE pur_order_line
    ADD COLUMN received_qty DECIMAL(18, 4) NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS pur_receipt (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    receipt_no VARCHAR(64) NOT NULL,
    order_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    receipt_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    total_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    total_tax_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS pur_receipt_line (
    id BIGINT PRIMARY KEY,
    receipt_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    order_line_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    qty DECIMAL(18, 4) NOT NULL,
    price DECIMAL(18, 2) NOT NULL,
    tax_rate DECIMAL(8, 4) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    tax_amount DECIMAL(18, 2) NOT NULL,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS inv_balance (
    id BIGINT PRIMARY KEY,
    warehouse_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    qty_on_hand DECIMAL(18, 4) NOT NULL DEFAULT 0,
    amount_on_hand DECIMAL(18, 2) NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS inv_txn (
    id BIGINT PRIMARY KEY,
    warehouse_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    biz_type VARCHAR(32) NOT NULL,
    biz_no VARCHAR(64) NOT NULL,
    biz_line_id BIGINT NOT NULL,
    direction VARCHAR(16) NOT NULL,
    qty DECIMAL(18, 4) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    unit_cost DECIMAL(18, 4) NOT NULL DEFAULT 0,
    occurred_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_pur_receipt_receipt_no ON pur_receipt (receipt_no);
CREATE INDEX idx_pur_receipt_order_id ON pur_receipt (order_id);
CREATE INDEX idx_pur_receipt_line_receipt_id ON pur_receipt_line (receipt_id);
CREATE UNIQUE INDEX uk_inv_balance_warehouse_id_product_id ON inv_balance (warehouse_id, product_id);
CREATE INDEX idx_inv_txn_biz_no ON inv_txn (biz_no);
