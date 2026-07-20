CREATE TABLE IF NOT EXISTS inv_transfer (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    transfer_no VARCHAR(64) NOT NULL,
    from_warehouse_id BIGINT NOT NULL,
    to_warehouse_id BIGINT NOT NULL,
    transfer_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'POSTED', 'CANCELLED')),
    total_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS inv_transfer_line (
    id BIGINT PRIMARY KEY,
    transfer_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    product_id BIGINT NOT NULL,
    qty DECIMAL(18, 4) NOT NULL,
    unit_cost DECIMAL(18, 4) NOT NULL DEFAULT 0,
    amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_inv_transfer_transfer_no ON inv_transfer (transfer_no);
CREATE INDEX idx_inv_transfer_from_warehouse_id ON inv_transfer (from_warehouse_id);
CREATE INDEX idx_inv_transfer_to_warehouse_id ON inv_transfer (to_warehouse_id);
CREATE INDEX idx_inv_transfer_line_transfer_id ON inv_transfer_line (transfer_id);
CREATE INDEX idx_inv_transfer_line_product_id ON inv_transfer_line (product_id);
