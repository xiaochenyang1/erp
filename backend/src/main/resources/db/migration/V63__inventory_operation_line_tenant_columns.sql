ALTER TABLE inv_adjustment_line
    ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE inv_adjustment_line
    ADD COLUMN account_book_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE inv_transfer_line
    ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE inv_transfer_line
    ADD COLUMN account_book_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE inv_stock_check_line
    ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE inv_stock_check_line
    ADD COLUMN account_book_id BIGINT NOT NULL DEFAULT 1;

UPDATE inv_adjustment_line
SET company_id = (
        SELECT inv_adjustment.company_id
        FROM inv_adjustment
        WHERE inv_adjustment.id = inv_adjustment_line.adjustment_id
    ),
    account_book_id = (
        SELECT inv_adjustment.account_book_id
        FROM inv_adjustment
        WHERE inv_adjustment.id = inv_adjustment_line.adjustment_id
    )
WHERE EXISTS (
    SELECT 1
    FROM inv_adjustment
    WHERE inv_adjustment.id = inv_adjustment_line.adjustment_id
);

UPDATE inv_transfer_line
SET company_id = (
        SELECT inv_transfer.company_id
        FROM inv_transfer
        WHERE inv_transfer.id = inv_transfer_line.transfer_id
    ),
    account_book_id = (
        SELECT inv_transfer.account_book_id
        FROM inv_transfer
        WHERE inv_transfer.id = inv_transfer_line.transfer_id
    )
WHERE EXISTS (
    SELECT 1
    FROM inv_transfer
    WHERE inv_transfer.id = inv_transfer_line.transfer_id
);

UPDATE inv_stock_check_line
SET company_id = (
        SELECT inv_stock_check.company_id
        FROM inv_stock_check
        WHERE inv_stock_check.id = inv_stock_check_line.check_id
    ),
    account_book_id = (
        SELECT inv_stock_check.account_book_id
        FROM inv_stock_check
        WHERE inv_stock_check.id = inv_stock_check_line.check_id
    )
WHERE EXISTS (
    SELECT 1
    FROM inv_stock_check
    WHERE inv_stock_check.id = inv_stock_check_line.check_id
);

CREATE INDEX idx_inv_adjustment_line_company_book_adjustment
    ON inv_adjustment_line (company_id, account_book_id, adjustment_id, line_no);

CREATE INDEX idx_inv_transfer_line_company_book_transfer
    ON inv_transfer_line (company_id, account_book_id, transfer_id, line_no);

CREATE INDEX idx_inv_stock_check_line_company_book_check
    ON inv_stock_check_line (company_id, account_book_id, check_id, line_no);
