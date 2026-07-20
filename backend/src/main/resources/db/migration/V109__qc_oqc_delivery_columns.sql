-- V109: 出库质检(OQC)最小扩展。
-- 复用 qc_inspection_order / qc_inspection_line：增加类型与 delivery 引用；
-- IQC 路径保持默认 inspection_type='IQC' 且 receipt 列继续使用；
-- OQC 使用 delivery_id / delivery_line_id，receipt 列可为空。
-- 过账策略：gate-only（出库过账前校验 JUDGED OQC），判定不回写出库行数量。

ALTER TABLE qc_inspection_order
    ADD COLUMN inspection_type VARCHAR(16) NOT NULL DEFAULT 'IQC',
    ADD COLUMN delivery_id BIGINT NULL,
    MODIFY COLUMN receipt_id BIGINT NULL;

ALTER TABLE qc_inspection_line
    ADD COLUMN delivery_line_id BIGINT NULL,
    MODIFY COLUMN receipt_line_id BIGINT NULL;

CREATE INDEX idx_qc_inspection_order_company_book_delivery_status
    ON qc_inspection_order (company_id, account_book_id, delivery_id, status);

CREATE INDEX idx_qc_inspection_order_company_book_type_status
    ON qc_inspection_order (company_id, account_book_id, inspection_type, status);

CREATE INDEX idx_qc_inspection_line_company_book_delivery_line
    ON qc_inspection_line (company_id, account_book_id, delivery_line_id);
