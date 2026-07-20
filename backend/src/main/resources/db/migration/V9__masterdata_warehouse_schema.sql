CREATE TABLE IF NOT EXISTS md_warehouse (
    id BIGINT PRIMARY KEY,
    warehouse_code VARCHAR(64) NOT NULL,
    warehouse_name VARCHAR(64) NOT NULL,
    dept_id BIGINT NOT NULL,
    manager_user_id BIGINT NOT NULL,
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

CREATE UNIQUE INDEX uk_md_warehouse_warehouse_code ON md_warehouse (warehouse_code);
CREATE INDEX idx_md_warehouse_dept_id ON md_warehouse (dept_id);
CREATE INDEX idx_md_warehouse_manager_user_id ON md_warehouse (manager_user_id);
