CREATE TABLE IF NOT EXISTS prd_work_center (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    work_center_code VARCHAR(64) NOT NULL,
    work_center_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS prd_routing (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    routing_code VARCHAR(64) NOT NULL,
    routing_name VARCHAR(128) NOT NULL,
    bom_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS prd_routing_operation (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    routing_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    operation_code VARCHAR(64) NOT NULL,
    operation_name VARCHAR(128) NOT NULL,
    work_center_id BIGINT NOT NULL,
    standard_minutes DECIMAL(18, 2) NOT NULL,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_prd_work_center_company_book_code
    ON prd_work_center (company_id, account_book_id, work_center_code);
CREATE INDEX idx_prd_work_center_company_book_status
    ON prd_work_center (company_id, account_book_id, status);

CREATE UNIQUE INDEX uk_prd_routing_company_book_code
    ON prd_routing (company_id, account_book_id, routing_code);
CREATE UNIQUE INDEX uk_prd_routing_company_book_bom
    ON prd_routing (company_id, account_book_id, bom_id);
CREATE INDEX idx_prd_routing_company_book_status
    ON prd_routing (company_id, account_book_id, status);

CREATE UNIQUE INDEX uk_prd_routing_operation_company_book_line
    ON prd_routing_operation (company_id, account_book_id, routing_id, line_no);
CREATE UNIQUE INDEX uk_prd_routing_operation_company_book_code
    ON prd_routing_operation (company_id, account_book_id, routing_id, operation_code);
CREATE INDEX idx_prd_routing_operation_company_book_work_center
    ON prd_routing_operation (company_id, account_book_id, work_center_id);
