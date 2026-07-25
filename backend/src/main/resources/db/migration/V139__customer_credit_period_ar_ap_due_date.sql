-- V139: 客户账期 + 应收/应付到期日
ALTER TABLE md_customer
    ADD COLUMN credit_period INT NULL;

ALTER TABLE fin_receivable
    ADD COLUMN due_date DATE NULL;

ALTER TABLE fin_payable
    ADD COLUMN due_date DATE NULL;

-- 历史数据默认：到期日=业务日（账期 0 / 空视为现结）
UPDATE fin_receivable
SET due_date = biz_date
WHERE due_date IS NULL AND biz_date IS NOT NULL;

UPDATE fin_payable
SET due_date = biz_date
WHERE due_date IS NULL AND biz_date IS NOT NULL;

CREATE INDEX idx_fin_receivable_due_date ON fin_receivable (company_id, account_book_id, due_date);
CREATE INDEX idx_fin_payable_due_date ON fin_payable (company_id, account_book_id, due_date);
