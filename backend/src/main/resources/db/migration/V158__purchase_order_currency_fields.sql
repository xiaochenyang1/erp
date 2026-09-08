ALTER TABLE pur_order ADD COLUMN currency_code VARCHAR(3) NULL;
ALTER TABLE pur_order ADD COLUMN exchange_rate DECIMAL(24,12) NULL;
ALTER TABLE pur_order ADD COLUMN base_total_amount DECIMAL(20,6) NULL;
ALTER TABLE pur_order ADD COLUMN base_total_tax_amount DECIMAL(20,6) NULL;
UPDATE pur_order SET currency_code = 'CNY', exchange_rate = 1, base_total_amount = total_amount, base_total_tax_amount = total_tax_amount WHERE currency_code IS NULL;
CREATE INDEX idx_pur_order_company_book_currency ON pur_order(company_id, account_book_id, currency_code, order_date);
