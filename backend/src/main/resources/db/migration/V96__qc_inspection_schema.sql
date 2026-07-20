-- V96: 采购来料质检(IQC)模块表结构 + md_product 需检验开关 + QC 单号规则。
-- 表结构对齐 pur_receipt/pur_receipt_line 风格:多租户列 company_id/account_book_id、
-- deleted_flag、version 与 4 审计列;唯一/普通索引均以 (company_id, account_book_id, ...) 打头。

ALTER TABLE md_product
    ADD COLUMN inspection_required TINYINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS qc_inspection_order (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    inspection_no VARCHAR(64) NOT NULL,
    receipt_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    supplier_id BIGINT,
    inspection_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    total_qty DECIMAL(18, 4) NOT NULL DEFAULT 0,
    qualified_qty DECIMAL(18, 4) NOT NULL DEFAULT 0,
    unqualified_qty DECIMAL(18, 4) NOT NULL DEFAULT 0,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS qc_inspection_line (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    inspection_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    receipt_line_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    inspected_qty DECIMAL(18, 4) NOT NULL DEFAULT 0,
    qualified_qty DECIMAL(18, 4) NOT NULL DEFAULT 0,
    unqualified_qty DECIMAL(18, 4) NOT NULL DEFAULT 0,
    defect_reason VARCHAR(255),
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_qc_inspection_order_company_book_no
    ON qc_inspection_order (company_id, account_book_id, inspection_no);
CREATE INDEX idx_qc_inspection_order_company_book_status
    ON qc_inspection_order (company_id, account_book_id, status);
CREATE INDEX idx_qc_inspection_order_company_book_receipt
    ON qc_inspection_order (company_id, account_book_id, receipt_id);

CREATE UNIQUE INDEX uk_qc_inspection_line_company_book_line
    ON qc_inspection_line (company_id, account_book_id, inspection_id, line_no);
CREATE INDEX idx_qc_inspection_line_company_book_inspection
    ON qc_inspection_line (company_id, account_book_id, inspection_id);

-- QC 检验单号规则(照 V27 列清单,id 用 2030 段)
INSERT INTO sys_sequence_rule
(id, biz_type, prefix, date_pattern, seq_length, current_value, status, created_by, updated_by, version)
VALUES
    (2030, 'QC_INSPECTION', 'QC', 'yyyyMMdd', 4, 1, 'ACTIVE', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    prefix = VALUES(prefix),
    date_pattern = VALUES(date_pattern),
    seq_length = VALUES(seq_length),
    current_value = GREATEST(current_value, VALUES(current_value)),
    status = VALUES(status);
