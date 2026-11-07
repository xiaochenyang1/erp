-- NULL means a legacy row without posting snapshots; zero is an authoritative posted amount.
-- Keep the six-decimal conversion snapshots and all historical vouchers unchanged.
ALTER TABLE fin_receipt_allocation
    ADD COLUMN posted_base_amount DECIMAL(18, 2) NULL;
ALTER TABLE fin_receipt_allocation
    ADD COLUMN posted_base_settled_amount DECIMAL(18, 2) NULL;
ALTER TABLE fin_receipt_allocation
    ADD COLUMN rounding_amount DECIMAL(18, 2) NULL;

ALTER TABLE fin_payment_allocation
    ADD COLUMN posted_base_amount DECIMAL(18, 2) NULL;
ALTER TABLE fin_payment_allocation
    ADD COLUMN posted_base_settled_amount DECIMAL(18, 2) NULL;
ALTER TABLE fin_payment_allocation
    ADD COLUMN rounding_amount DECIMAL(18, 2) NULL;

ALTER TABLE fin_receivable ADD COLUMN posted_base_settled_amount DECIMAL(18, 2) NULL;
ALTER TABLE fin_payable ADD COLUMN posted_base_settled_amount DECIMAL(18, 2) NULL;
