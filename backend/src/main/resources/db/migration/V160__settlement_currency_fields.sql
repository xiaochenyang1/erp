ALTER TABLE fin_receipt ADD COLUMN currency_code VARCHAR(3) NULL;
ALTER TABLE fin_receipt ADD COLUMN exchange_rate DECIMAL(24,12) NULL;
ALTER TABLE fin_receipt ADD COLUMN base_amount DECIMAL(20,6) NULL;
ALTER TABLE fin_payment ADD COLUMN currency_code VARCHAR(3) NULL;
ALTER TABLE fin_payment ADD COLUMN exchange_rate DECIMAL(24,12) NULL;
ALTER TABLE fin_payment ADD COLUMN base_amount DECIMAL(20,6) NULL;
UPDATE fin_receipt SET currency_code='CNY', exchange_rate=1, base_amount=amount WHERE currency_code IS NULL;
UPDATE fin_payment SET currency_code='CNY', exchange_rate=1, base_amount=amount WHERE currency_code IS NULL;
