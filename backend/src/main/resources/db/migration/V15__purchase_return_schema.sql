ALTER TABLE pur_receipt_line
    ADD COLUMN returned_qty DECIMAL(18, 4) NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS pur_return (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    return_no VARCHAR(64) NOT NULL,
    receipt_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    return_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'POSTED', 'CANCELLED')),
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

CREATE TABLE IF NOT EXISTS pur_return_line (
    id BIGINT PRIMARY KEY,
    return_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    receipt_line_id BIGINT NOT NULL,
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

CREATE UNIQUE INDEX uk_pur_return_return_no ON pur_return (return_no);
CREATE INDEX idx_pur_return_receipt_id ON pur_return (receipt_id);
CREATE INDEX idx_pur_return_line_return_id ON pur_return_line (return_id);
CREATE INDEX idx_pur_return_line_receipt_line_id ON pur_return_line (receipt_line_id);
