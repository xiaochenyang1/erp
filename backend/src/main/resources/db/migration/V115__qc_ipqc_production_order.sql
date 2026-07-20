-- V115: IPQC 过程检 — 检验单关联生产工单
ALTER TABLE qc_inspection_order
    ADD COLUMN production_order_id BIGINT NULL;

CREATE INDEX idx_qc_inspection_production_order
    ON qc_inspection_order (company_id, account_book_id, production_order_id, status);
