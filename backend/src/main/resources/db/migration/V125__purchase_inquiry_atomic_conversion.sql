-- V125: 询价单原子转换采购订单。
-- 双向保存结构化来源，并用租户/账套级唯一索引保证一张询价单最多生成一张采购订单。

ALTER TABLE pur_inquiry
    ADD COLUMN converted_order_id BIGINT NULL;

ALTER TABLE pur_inquiry
    ADD COLUMN converted_order_no VARCHAR(64) NULL;

ALTER TABLE pur_inquiry
    ADD COLUMN converted_by BIGINT NULL;

ALTER TABLE pur_inquiry
    ADD COLUMN converted_time TIMESTAMP NULL;

ALTER TABLE pur_order
    ADD COLUMN source_inquiry_id BIGINT NULL;

ALTER TABLE pur_order
    ADD COLUMN source_inquiry_no VARCHAR(64) NULL;

ALTER TABLE pur_order
    ADD COLUMN source_quote_id BIGINT NULL;

ALTER TABLE pur_order_line
    ADD COLUMN source_inquiry_id BIGINT NULL;

ALTER TABLE pur_order_line
    ADD COLUMN source_inquiry_line_id BIGINT NULL;

CREATE UNIQUE INDEX uk_pur_order_company_book_source_inquiry
    ON pur_order (company_id, account_book_id, source_inquiry_id);

CREATE UNIQUE INDEX uk_pur_inquiry_company_book_converted_order
    ON pur_inquiry (company_id, account_book_id, converted_order_id);

CREATE UNIQUE INDEX uk_pur_order_line_company_book_source_inquiry_line
    ON pur_order_line (company_id, account_book_id, source_inquiry_line_id);

