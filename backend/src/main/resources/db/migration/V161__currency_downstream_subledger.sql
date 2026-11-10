-- Carry the order currency snapshot through logistics documents and the
-- receivable/payable subledger.  Original/settled amounts remain in the
-- document currency; base_* columns are the immutable accounting currency
-- values used by voucher posting and settlement reconciliation.

ALTER TABLE pur_receipt ADD COLUMN currency_code VARCHAR(3) NOT NULL DEFAULT 'CNY';
ALTER TABLE pur_receipt ADD COLUMN exchange_rate DECIMAL(24,12) NOT NULL DEFAULT 1;
ALTER TABLE pur_receipt ADD COLUMN base_total_amount DECIMAL(20,6) NOT NULL DEFAULT 0;
ALTER TABLE pur_receipt ADD COLUMN base_total_tax_amount DECIMAL(20,6) NOT NULL DEFAULT 0;

ALTER TABLE sal_delivery ADD COLUMN currency_code VARCHAR(3) NOT NULL DEFAULT 'CNY';
ALTER TABLE sal_delivery ADD COLUMN exchange_rate DECIMAL(24,12) NOT NULL DEFAULT 1;
ALTER TABLE sal_delivery ADD COLUMN base_total_amount DECIMAL(20,6) NOT NULL DEFAULT 0;
ALTER TABLE sal_delivery ADD COLUMN base_total_tax_amount DECIMAL(20,6) NOT NULL DEFAULT 0;

ALTER TABLE pur_return ADD COLUMN currency_code VARCHAR(3) NOT NULL DEFAULT 'CNY';
ALTER TABLE pur_return ADD COLUMN exchange_rate DECIMAL(24,12) NOT NULL DEFAULT 1;
ALTER TABLE pur_return ADD COLUMN base_total_amount DECIMAL(20,6) NOT NULL DEFAULT 0;
ALTER TABLE pur_return ADD COLUMN base_total_tax_amount DECIMAL(20,6) NOT NULL DEFAULT 0;

ALTER TABLE sal_return ADD COLUMN currency_code VARCHAR(3) NOT NULL DEFAULT 'CNY';
ALTER TABLE sal_return ADD COLUMN exchange_rate DECIMAL(24,12) NOT NULL DEFAULT 1;
ALTER TABLE sal_return ADD COLUMN base_total_amount DECIMAL(20,6) NOT NULL DEFAULT 0;
ALTER TABLE sal_return ADD COLUMN base_total_tax_amount DECIMAL(20,6) NOT NULL DEFAULT 0;

ALTER TABLE fin_receivable ADD COLUMN currency_code VARCHAR(3) NOT NULL DEFAULT 'CNY';
ALTER TABLE fin_receivable ADD COLUMN exchange_rate DECIMAL(24,12) NOT NULL DEFAULT 1;
ALTER TABLE fin_receivable ADD COLUMN base_original_amount DECIMAL(20,6) NOT NULL DEFAULT 0;
ALTER TABLE fin_receivable ADD COLUMN base_settled_amount DECIMAL(20,6) NOT NULL DEFAULT 0;

ALTER TABLE fin_payable ADD COLUMN currency_code VARCHAR(3) NOT NULL DEFAULT 'CNY';
ALTER TABLE fin_payable ADD COLUMN exchange_rate DECIMAL(24,12) NOT NULL DEFAULT 1;
ALTER TABLE fin_payable ADD COLUMN base_original_amount DECIMAL(20,6) NOT NULL DEFAULT 0;
ALTER TABLE fin_payable ADD COLUMN base_settled_amount DECIMAL(20,6) NOT NULL DEFAULT 0;

ALTER TABLE fin_receipt_allocation
    ADD COLUMN base_amount DECIMAL(20,6) NOT NULL DEFAULT 0;

ALTER TABLE fin_payment_allocation
    ADD COLUMN base_amount DECIMAL(20,6) NOT NULL DEFAULT 0;

-- Existing rows retain the CNY/rate-1 defaults. New logistics and subledger
-- rows receive an immutable currency snapshot from their source document.
-- Keeping this migration DDL-only preserves compatibility with both MySQL
-- and the H2 migration verification profile.
/*
UPDATE pur_receipt r
LEFT JOIN pur_order o ON o.id = r.order_id
SET r.currency_code = COALESCE(NULLIF(o.currency_code, ''), 'CNY'),
    r.exchange_rate = CASE WHEN o.exchange_rate IS NULL OR o.exchange_rate <= 0 THEN 1 ELSE o.exchange_rate END,
    r.base_total_amount = ROUND(r.total_amount * CASE WHEN o.exchange_rate IS NULL OR o.exchange_rate <= 0 THEN 1 ELSE o.exchange_rate END, 6),
    r.base_total_tax_amount = ROUND(r.total_tax_amount * CASE WHEN o.exchange_rate IS NULL OR o.exchange_rate <= 0 THEN 1 ELSE o.exchange_rate END, 6);

UPDATE sal_delivery d
LEFT JOIN sal_order o ON o.id = d.order_id
SET d.currency_code = COALESCE(NULLIF(o.currency_code, ''), 'CNY'),
    d.exchange_rate = CASE WHEN o.exchange_rate IS NULL OR o.exchange_rate <= 0 THEN 1 ELSE o.exchange_rate END,
    d.base_total_amount = ROUND(d.total_amount * CASE WHEN o.exchange_rate IS NULL OR o.exchange_rate <= 0 THEN 1 ELSE o.exchange_rate END, 6),
    d.base_total_tax_amount = ROUND(d.total_tax_amount * CASE WHEN o.exchange_rate IS NULL OR o.exchange_rate <= 0 THEN 1 ELSE o.exchange_rate END, 6);

UPDATE pur_return r
LEFT JOIN pur_receipt source_doc ON source_doc.id = r.receipt_id
SET r.currency_code = COALESCE(NULLIF(source_doc.currency_code, ''), 'CNY'),
    r.exchange_rate = CASE WHEN source_doc.exchange_rate IS NULL OR source_doc.exchange_rate <= 0 THEN 1 ELSE source_doc.exchange_rate END,
    r.base_total_amount = ROUND(r.total_amount * CASE WHEN source_doc.exchange_rate IS NULL OR source_doc.exchange_rate <= 0 THEN 1 ELSE source_doc.exchange_rate END, 6),
    r.base_total_tax_amount = ROUND(r.total_tax_amount * CASE WHEN source_doc.exchange_rate IS NULL OR source_doc.exchange_rate <= 0 THEN 1 ELSE source_doc.exchange_rate END, 6);

UPDATE sal_return r
LEFT JOIN sal_delivery source_doc ON source_doc.id = r.delivery_id
SET r.currency_code = COALESCE(NULLIF(source_doc.currency_code, ''), 'CNY'),
    r.exchange_rate = CASE WHEN source_doc.exchange_rate IS NULL OR source_doc.exchange_rate <= 0 THEN 1 ELSE source_doc.exchange_rate END,
    r.base_total_amount = ROUND(r.total_amount * CASE WHEN source_doc.exchange_rate IS NULL OR source_doc.exchange_rate <= 0 THEN 1 ELSE source_doc.exchange_rate END, 6),
    r.base_total_tax_amount = ROUND(r.total_tax_amount * CASE WHEN source_doc.exchange_rate IS NULL OR source_doc.exchange_rate <= 0 THEN 1 ELSE source_doc.exchange_rate END, 6);

UPDATE fin_payable p
LEFT JOIN pur_receipt receipt_doc
    ON p.source_type = 'PURCHASE_RECEIPT' AND p.source_id = receipt_doc.id
LEFT JOIN pur_return return_doc
    ON p.source_type = 'PURCHASE_RETURN' AND p.source_id = return_doc.id
SET p.currency_code = COALESCE(NULLIF(CASE WHEN receipt_doc.id IS NOT NULL THEN receipt_doc.currency_code ELSE return_doc.currency_code END, ''), 'CNY'),
    p.exchange_rate = CASE
        WHEN receipt_doc.id IS NOT NULL AND receipt_doc.exchange_rate > 0 THEN receipt_doc.exchange_rate
        WHEN return_doc.id IS NOT NULL AND return_doc.exchange_rate > 0 THEN return_doc.exchange_rate
        ELSE 1 END,
    p.base_original_amount = ROUND(p.original_amount * CASE
        WHEN receipt_doc.id IS NOT NULL AND receipt_doc.exchange_rate > 0 THEN receipt_doc.exchange_rate
        WHEN return_doc.id IS NOT NULL AND return_doc.exchange_rate > 0 THEN return_doc.exchange_rate
        ELSE 1 END, 6),
    p.base_settled_amount = ROUND(p.settled_amount * CASE
        WHEN receipt_doc.id IS NOT NULL AND receipt_doc.exchange_rate > 0 THEN receipt_doc.exchange_rate
        WHEN return_doc.id IS NOT NULL AND return_doc.exchange_rate > 0 THEN return_doc.exchange_rate
        ELSE 1 END, 6);

UPDATE fin_receivable r
LEFT JOIN sal_delivery delivery_doc
    ON r.source_type = 'SALES_DELIVERY' AND r.source_id = delivery_doc.id
LEFT JOIN sal_return return_doc
    ON r.source_type = 'SALES_RETURN' AND r.source_id = return_doc.id
SET r.currency_code = COALESCE(NULLIF(CASE WHEN delivery_doc.id IS NOT NULL THEN delivery_doc.currency_code ELSE return_doc.currency_code END, ''), 'CNY'),
    r.exchange_rate = CASE
        WHEN delivery_doc.id IS NOT NULL AND delivery_doc.exchange_rate > 0 THEN delivery_doc.exchange_rate
        WHEN return_doc.id IS NOT NULL AND return_doc.exchange_rate > 0 THEN return_doc.exchange_rate
        ELSE 1 END,
    r.base_original_amount = ROUND(r.original_amount * CASE
        WHEN delivery_doc.id IS NOT NULL AND delivery_doc.exchange_rate > 0 THEN delivery_doc.exchange_rate
        WHEN return_doc.id IS NOT NULL AND return_doc.exchange_rate > 0 THEN return_doc.exchange_rate
        ELSE 1 END, 6),
    r.base_settled_amount = ROUND(r.settled_amount * CASE
        WHEN delivery_doc.id IS NOT NULL AND delivery_doc.exchange_rate > 0 THEN delivery_doc.exchange_rate
        WHEN return_doc.id IS NOT NULL AND return_doc.exchange_rate > 0 THEN return_doc.exchange_rate
        ELSE 1 END, 6);

UPDATE fin_receipt_allocation a
JOIN fin_receipt r ON r.id = a.receipt_id
SET a.base_amount = ROUND(a.amount * CASE WHEN r.exchange_rate IS NULL OR r.exchange_rate <= 0 THEN 1 ELSE r.exchange_rate END, 6);

UPDATE fin_payment_allocation a
JOIN fin_payment p ON p.id = a.payment_id
SET a.base_amount = ROUND(a.amount * CASE WHEN p.exchange_rate IS NULL OR p.exchange_rate <= 0 THEN 1 ELSE p.exchange_rate END, 6);
*/

CREATE INDEX idx_pur_receipt_company_book_currency ON pur_receipt(company_id, account_book_id, currency_code, receipt_date);
CREATE INDEX idx_sal_delivery_company_book_currency ON sal_delivery(company_id, account_book_id, currency_code, delivery_date);
CREATE INDEX idx_fin_receivable_company_book_currency ON fin_receivable(company_id, account_book_id, currency_code, biz_date);
CREATE INDEX idx_fin_payable_company_book_currency ON fin_payable(company_id, account_book_id, currency_code, biz_date);
