CREATE TABLE IF NOT EXISTS md_product (
    id BIGINT PRIMARY KEY,
    product_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(128) NOT NULL,
    product_type VARCHAR(32) NOT NULL,
    category_name VARCHAR(64) NOT NULL,
    specification VARCHAR(128),
    unit_name VARCHAR(32) NOT NULL,
    purchase_price DECIMAL(18, 2) NOT NULL,
    sale_price DECIMAL(18, 2) NOT NULL,
    tax_rate DECIMAL(8, 4) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_md_product_product_code ON md_product (product_code);
CREATE INDEX idx_md_product_category_name ON md_product (category_name);
