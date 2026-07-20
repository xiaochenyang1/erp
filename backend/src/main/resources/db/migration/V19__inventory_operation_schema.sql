CREATE TABLE IF NOT EXISTS inv_adjustment (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    adjustment_no VARCHAR(64) NOT NULL,
    warehouse_id BIGINT NOT NULL,
    adjustment_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'POSTED', 'CANCELLED')),
    total_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    source_type VARCHAR(64),
    source_id BIGINT,
    source_no VARCHAR(64),
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS inv_adjustment_line (
    id BIGINT PRIMARY KEY,
    adjustment_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    product_id BIGINT NOT NULL,
    direction VARCHAR(16) NOT NULL CHECK (direction IN ('IN', 'OUT')),
    qty DECIMAL(18, 4) NOT NULL,
    unit_cost DECIMAL(18, 4) NOT NULL DEFAULT 0,
    amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    reason VARCHAR(255),
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS inv_stock_check (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    check_no VARCHAR(64) NOT NULL,
    warehouse_id BIGINT NOT NULL,
    check_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'COUNTED', 'ADJUSTED', 'CANCELLED')),
    generated_adjustment_id BIGINT,
    generated_adjustment_no VARCHAR(64),
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS inv_stock_check_line (
    id BIGINT PRIMARY KEY,
    check_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    product_id BIGINT NOT NULL,
    book_qty DECIMAL(18, 4) NOT NULL DEFAULT 0,
    actual_qty DECIMAL(18, 4) NOT NULL DEFAULT 0,
    difference_qty DECIMAL(18, 4) NOT NULL DEFAULT 0,
    unit_cost DECIMAL(18, 4) NOT NULL DEFAULT 0,
    difference_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS inv_alert_rule (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    warehouse_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    min_qty DECIMAL(18, 4) NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_inv_adjustment_adjustment_no ON inv_adjustment (adjustment_no);
CREATE INDEX idx_inv_adjustment_warehouse_id ON inv_adjustment (warehouse_id);
CREATE INDEX idx_inv_adjustment_source ON inv_adjustment (source_type, source_id);
CREATE INDEX idx_inv_adjustment_line_adjustment_id ON inv_adjustment_line (adjustment_id);
CREATE INDEX idx_inv_adjustment_line_product_id ON inv_adjustment_line (product_id);

CREATE UNIQUE INDEX uk_inv_stock_check_check_no ON inv_stock_check (check_no);
CREATE INDEX idx_inv_stock_check_warehouse_id ON inv_stock_check (warehouse_id);
CREATE INDEX idx_inv_stock_check_line_check_id ON inv_stock_check_line (check_id);
CREATE INDEX idx_inv_stock_check_line_product_id ON inv_stock_check_line (product_id);

CREATE UNIQUE INDEX uk_inv_alert_rule_product_warehouse ON inv_alert_rule (product_id, warehouse_id);
CREATE INDEX idx_inv_alert_rule_warehouse_id ON inv_alert_rule (warehouse_id);
