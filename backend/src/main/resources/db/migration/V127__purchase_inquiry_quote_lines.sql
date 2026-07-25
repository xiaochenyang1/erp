-- V127: 采购询价按明细报价。
-- 报价明细同时保存租户/账套/询价冗余键，并通过复合外键保证 quote 与 inquiry_line 均属于同一询价。

CREATE UNIQUE INDEX uk_pur_inquiry_company_book_id
    ON pur_inquiry (company_id, account_book_id, id);

CREATE UNIQUE INDEX uk_pur_inquiry_quote_company_book_inquiry_id
    ON pur_inquiry_quote (company_id, account_book_id, inquiry_id, id);

CREATE UNIQUE INDEX uk_pur_inquiry_line_company_book_inquiry_id
    ON pur_inquiry_line (company_id, account_book_id, inquiry_id, id);

CREATE TABLE pur_inquiry_quote_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    inquiry_id BIGINT NOT NULL,
    quote_id BIGINT NOT NULL,
    inquiry_line_id BIGINT NOT NULL,
    unit_price DECIMAL(18, 2) NOT NULL,
    tax_rate DECIMAL(8, 4) NOT NULL DEFAULT 0,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT ck_pur_inquiry_quote_line_unit_price CHECK (unit_price >= 0),
    CONSTRAINT ck_pur_inquiry_quote_line_tax_rate CHECK (tax_rate >= 0),
    CONSTRAINT fk_pur_inquiry_quote_line_inquiry
        FOREIGN KEY (company_id, account_book_id, inquiry_id)
        REFERENCES pur_inquiry (company_id, account_book_id, id),
    CONSTRAINT fk_pur_inquiry_quote_line_quote
        FOREIGN KEY (company_id, account_book_id, inquiry_id, quote_id)
        REFERENCES pur_inquiry_quote (company_id, account_book_id, inquiry_id, id),
    CONSTRAINT fk_pur_inquiry_quote_line_inquiry_line
        FOREIGN KEY (company_id, account_book_id, inquiry_id, inquiry_line_id)
        REFERENCES pur_inquiry_line (company_id, account_book_id, inquiry_id, id)
);

CREATE UNIQUE INDEX uk_pur_inquiry_quote_line_company_book_quote_line
    ON pur_inquiry_quote_line (company_id, account_book_id, quote_id, inquiry_line_id);

CREATE INDEX idx_pur_inquiry_quote_line_company_book_inquiry
    ON pur_inquiry_quote_line (company_id, account_book_id, inquiry_id, deleted_flag);

CREATE INDEX idx_pur_inquiry_quote_line_company_book_quote
    ON pur_inquiry_quote_line (company_id, account_book_id, quote_id, deleted_flag);

-- 兼容 V127 之前的 header 级报价：同一报价价格展开到该询价的每条有效明细。
-- 三张父表均使用 company_id/account_book_id/inquiry_id 复合联接，禁止跨租户或跨询价串价。
INSERT INTO pur_inquiry_quote_line (
    company_id, account_book_id, inquiry_id, quote_id, inquiry_line_id,
    unit_price, tax_rate, deleted_flag,
    created_by, created_time, updated_by, updated_time, version
)
SELECT
    quote.company_id,
    quote.account_book_id,
    quote.inquiry_id,
    quote.id,
    inquiry_line.id,
    quote.unit_price,
    COALESCE(quote.tax_rate, 0),
    0,
    quote.created_by,
    quote.created_time,
    quote.updated_by,
    quote.updated_time,
    0
FROM pur_inquiry_quote quote
JOIN pur_inquiry inquiry
  ON inquiry.company_id = quote.company_id
 AND inquiry.account_book_id = quote.account_book_id
 AND inquiry.id = quote.inquiry_id
 AND inquiry.deleted_flag = 0
JOIN pur_inquiry_line inquiry_line
  ON inquiry_line.company_id = quote.company_id
 AND inquiry_line.account_book_id = quote.account_book_id
 AND inquiry_line.inquiry_id = quote.inquiry_id
 AND inquiry_line.deleted_flag = 0
WHERE quote.deleted_flag = 0
  AND quote.unit_price IS NOT NULL;
