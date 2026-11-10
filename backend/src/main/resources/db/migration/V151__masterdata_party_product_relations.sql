-- V151: 客户/供应商商品关系与采购履约规则
CREATE TABLE IF NOT EXISTS md_customer_product_relation (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    customer_product_code VARCHAR(64),
    customer_product_name VARCHAR(128),
    delivery_preference VARCHAR(255),
    packaging_preference VARCHAR(255),
    remark VARCHAR(255),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_md_customer_product_relation_scope ON md_customer_product_relation (company_id, account_book_id, customer_id, product_id);
CREATE INDEX idx_md_customer_product_relation_product ON md_customer_product_relation (company_id, account_book_id, product_id, status, deleted_flag);

CREATE TABLE IF NOT EXISTS md_supplier_product_relation (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    supplier_product_code VARCHAR(64),
    supplier_product_name VARCHAR(128),
    min_purchase_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    lead_time_days INT NOT NULL DEFAULT 0,
    default_supplier_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_md_supplier_product_relation_qty CHECK (min_purchase_qty >= 0),
    CONSTRAINT chk_md_supplier_product_relation_lead CHECK (lead_time_days >= 0)
);
CREATE UNIQUE INDEX uk_md_supplier_product_relation_scope ON md_supplier_product_relation (company_id, account_book_id, supplier_id, product_id);
CREATE INDEX idx_md_supplier_product_relation_product ON md_supplier_product_relation (company_id, account_book_id, product_id, status, default_supplier_flag, deleted_flag);
