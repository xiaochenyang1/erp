CREATE TABLE IF NOT EXISTS pur_order (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    order_no VARCHAR(64) NOT NULL,
    supplier_id BIGINT NOT NULL,
    order_date DATE NOT NULL,
    delivery_date DATE,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    approval_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SUBMITTED',
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

CREATE TABLE IF NOT EXISTS pur_order_line (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    product_id BIGINT NOT NULL,
    qty DECIMAL(18, 4) NOT NULL,
    price DECIMAL(18, 2) NOT NULL,
    tax_rate DECIMAL(8, 4) NOT NULL,
    tax_amount DECIMAL(18, 2) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_pur_order_order_no ON pur_order (order_no);
CREATE INDEX idx_pur_order_supplier_id ON pur_order (supplier_id);
CREATE INDEX idx_pur_order_order_date ON pur_order (order_date);
CREATE INDEX idx_pur_order_status ON pur_order (status);
CREATE INDEX idx_pur_order_line_order_id ON pur_order_line (order_id);
