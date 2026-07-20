ALTER TABLE fin_payment_allocation
    ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE fin_payment_allocation
    ADD COLUMN account_book_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE fin_receipt_allocation
    ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE fin_receipt_allocation
    ADD COLUMN account_book_id BIGINT NOT NULL DEFAULT 1;

UPDATE fin_payment_allocation
SET company_id = (
        SELECT fin_payment.company_id
        FROM fin_payment
        WHERE fin_payment.id = fin_payment_allocation.payment_id
    ),
    account_book_id = (
        SELECT fin_payment.account_book_id
        FROM fin_payment
        WHERE fin_payment.id = fin_payment_allocation.payment_id
    )
WHERE EXISTS (
    SELECT 1
    FROM fin_payment
    WHERE fin_payment.id = fin_payment_allocation.payment_id
);

UPDATE fin_receipt_allocation
SET company_id = (
        SELECT fin_receipt.company_id
        FROM fin_receipt
        WHERE fin_receipt.id = fin_receipt_allocation.receipt_id
    ),
    account_book_id = (
        SELECT fin_receipt.account_book_id
        FROM fin_receipt
        WHERE fin_receipt.id = fin_receipt_allocation.receipt_id
    )
WHERE EXISTS (
    SELECT 1
    FROM fin_receipt
    WHERE fin_receipt.id = fin_receipt_allocation.receipt_id
);

CREATE INDEX idx_fin_payment_alloc_company_book_payment
    ON fin_payment_allocation (company_id, account_book_id, payment_id);

CREATE INDEX idx_fin_payment_alloc_company_book_payable
    ON fin_payment_allocation (company_id, account_book_id, payable_id);

CREATE INDEX idx_fin_receipt_alloc_company_book_receipt
    ON fin_receipt_allocation (company_id, account_book_id, receipt_id);

CREATE INDEX idx_fin_receipt_alloc_company_book_receivable
    ON fin_receipt_allocation (company_id, account_book_id, receivable_id);
