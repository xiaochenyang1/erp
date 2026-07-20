CREATE TABLE IF NOT EXISTS md_customer (
    id BIGINT PRIMARY KEY,
    customer_code VARCHAR(64) NOT NULL,
    customer_name VARCHAR(128) NOT NULL,
    contact_name VARCHAR(64),
    contact_phone VARCHAR(32),
    settlement_method VARCHAR(32) NOT NULL,
    credit_limit DECIMAL(18, 2) NOT NULL,
    address VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_md_customer_customer_code ON md_customer (customer_code);
CREATE INDEX idx_md_customer_customer_name ON md_customer (customer_name);
CREATE INDEX idx_md_customer_contact_phone ON md_customer (contact_phone);
