CREATE TABLE IF NOT EXISTS md_supplier (
    id BIGINT PRIMARY KEY,
    supplier_code VARCHAR(64) NOT NULL,
    supplier_name VARCHAR(128) NOT NULL,
    contact_name VARCHAR(64),
    contact_phone VARCHAR(32),
    settlement_method VARCHAR(32) NOT NULL,
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

CREATE UNIQUE INDEX uk_md_supplier_supplier_code ON md_supplier (supplier_code);
CREATE INDEX idx_md_supplier_supplier_name ON md_supplier (supplier_name);
CREATE INDEX idx_md_supplier_contact_phone ON md_supplier (contact_phone);
