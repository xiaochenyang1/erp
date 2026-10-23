-- V163: Invoice and contract currency snapshot (Phase 1 downstream multi-currency)
-- Add nullable currency fields to fin_invoice_register and biz_contract.
-- Backfill existing rows to CNY/1 with base amounts copied from amount/totalAmount.
-- Create indexes for fast lookup by company/book/currency/date.

ALTER TABLE fin_invoice_register
    ADD COLUMN currency_code VARCHAR(3) NULL;
ALTER TABLE fin_invoice_register ADD COLUMN exchange_rate DECIMAL(24,12) NULL;
ALTER TABLE fin_invoice_register ADD COLUMN base_amount DECIMAL(20,6) NULL;
ALTER TABLE fin_invoice_register ADD COLUMN base_tax_amount DECIMAL(20,6) NULL;

UPDATE fin_invoice_register
SET currency_code = 'CNY',
    exchange_rate = 1,
    base_amount = amount,
    base_tax_amount = tax_amount
WHERE currency_code IS NULL;

CREATE INDEX idx_fin_invoice_register_company_book_currency
    ON fin_invoice_register(company_id, account_book_id, currency_code, invoice_date);

ALTER TABLE biz_contract
    ADD COLUMN currency_code VARCHAR(3) NULL;
ALTER TABLE biz_contract ADD COLUMN exchange_rate DECIMAL(24,12) NULL;
ALTER TABLE biz_contract ADD COLUMN base_total_amount DECIMAL(20,6) NULL;

UPDATE biz_contract
SET currency_code = 'CNY',
    exchange_rate = 1,
    base_total_amount = total_amount
WHERE currency_code IS NULL;

CREATE INDEX idx_biz_contract_company_book_currency
    ON biz_contract(company_id, account_book_id, currency_code, signed_date);
