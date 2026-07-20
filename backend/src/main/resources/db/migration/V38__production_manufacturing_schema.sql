CREATE TABLE IF NOT EXISTS prd_bom (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    bom_no VARCHAR(64) NOT NULL,
    product_id BIGINT NOT NULL,
    base_qty DECIMAL(18, 4) NOT NULL,
    status VARCHAR(32) NOT NULL,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS prd_bom_line (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    bom_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_product_id BIGINT NOT NULL,
    qty_per DECIMAL(18, 4) NOT NULL,
    loss_rate DECIMAL(18, 4) NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS prd_order (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    order_no VARCHAR(64) NOT NULL,
    bom_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    material_warehouse_id BIGINT NOT NULL,
    finished_warehouse_id BIGINT NOT NULL,
    planned_qty DECIMAL(18, 4) NOT NULL,
    completed_qty DECIMAL(18, 4) NOT NULL DEFAULT 0,
    planned_start_date DATE NOT NULL,
    planned_finish_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL,
    issued_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    finished_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS prd_order_material (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    order_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_product_id BIGINT NOT NULL,
    required_qty DECIMAL(18, 4) NOT NULL,
    issued_qty DECIMAL(18, 4) NOT NULL DEFAULT 0,
    issued_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_prd_bom_company_bom_no ON prd_bom (company_id, bom_no);
CREATE INDEX idx_prd_bom_company_product ON prd_bom (company_id, product_id, status);
CREATE UNIQUE INDEX uk_prd_bom_line_company_bom_line ON prd_bom_line (company_id, bom_id, line_no);
CREATE UNIQUE INDEX uk_prd_bom_line_company_bom_material ON prd_bom_line (company_id, bom_id, material_product_id);
CREATE UNIQUE INDEX uk_prd_order_company_order_no ON prd_order (company_id, order_no);
CREATE INDEX idx_prd_order_company_status ON prd_order (company_id, status, planned_start_date);
CREATE INDEX idx_prd_order_company_warehouses ON prd_order (company_id, material_warehouse_id, finished_warehouse_id);
CREATE UNIQUE INDEX uk_prd_order_material_company_order_line ON prd_order_material (company_id, order_id, line_no);
CREATE INDEX idx_prd_order_material_company_order ON prd_order_material (company_id, order_id);
