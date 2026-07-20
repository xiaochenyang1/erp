ALTER TABLE pur_order_line
    ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE pur_order_line
    ADD COLUMN account_book_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE pur_receipt_line
    ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE pur_receipt_line
    ADD COLUMN account_book_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE pur_return_line
    ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE pur_return_line
    ADD COLUMN account_book_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE sal_order_line
    ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE sal_order_line
    ADD COLUMN account_book_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE sal_delivery_line
    ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE sal_delivery_line
    ADD COLUMN account_book_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE sal_return_line
    ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE sal_return_line
    ADD COLUMN account_book_id BIGINT NOT NULL DEFAULT 1;

UPDATE pur_order_line
SET company_id = (
        SELECT pur_order.company_id
        FROM pur_order
        WHERE pur_order.id = pur_order_line.order_id
    ),
    account_book_id = (
        SELECT pur_order.account_book_id
        FROM pur_order
        WHERE pur_order.id = pur_order_line.order_id
    )
WHERE EXISTS (
    SELECT 1
    FROM pur_order
    WHERE pur_order.id = pur_order_line.order_id
);

UPDATE pur_receipt_line
SET company_id = (
        SELECT pur_receipt.company_id
        FROM pur_receipt
        WHERE pur_receipt.id = pur_receipt_line.receipt_id
    ),
    account_book_id = (
        SELECT pur_receipt.account_book_id
        FROM pur_receipt
        WHERE pur_receipt.id = pur_receipt_line.receipt_id
    )
WHERE EXISTS (
    SELECT 1
    FROM pur_receipt
    WHERE pur_receipt.id = pur_receipt_line.receipt_id
);

UPDATE pur_return_line
SET company_id = (
        SELECT pur_return.company_id
        FROM pur_return
        WHERE pur_return.id = pur_return_line.return_id
    ),
    account_book_id = (
        SELECT pur_return.account_book_id
        FROM pur_return
        WHERE pur_return.id = pur_return_line.return_id
    )
WHERE EXISTS (
    SELECT 1
    FROM pur_return
    WHERE pur_return.id = pur_return_line.return_id
);

UPDATE sal_order_line
SET company_id = (
        SELECT sal_order.company_id
        FROM sal_order
        WHERE sal_order.id = sal_order_line.order_id
    ),
    account_book_id = (
        SELECT sal_order.account_book_id
        FROM sal_order
        WHERE sal_order.id = sal_order_line.order_id
    )
WHERE EXISTS (
    SELECT 1
    FROM sal_order
    WHERE sal_order.id = sal_order_line.order_id
);

UPDATE sal_delivery_line
SET company_id = (
        SELECT sal_delivery.company_id
        FROM sal_delivery
        WHERE sal_delivery.id = sal_delivery_line.delivery_id
    ),
    account_book_id = (
        SELECT sal_delivery.account_book_id
        FROM sal_delivery
        WHERE sal_delivery.id = sal_delivery_line.delivery_id
    )
WHERE EXISTS (
    SELECT 1
    FROM sal_delivery
    WHERE sal_delivery.id = sal_delivery_line.delivery_id
);

UPDATE sal_return_line
SET company_id = (
        SELECT sal_return.company_id
        FROM sal_return
        WHERE sal_return.id = sal_return_line.return_id
    ),
    account_book_id = (
        SELECT sal_return.account_book_id
        FROM sal_return
        WHERE sal_return.id = sal_return_line.return_id
    )
WHERE EXISTS (
    SELECT 1
    FROM sal_return
    WHERE sal_return.id = sal_return_line.return_id
);

CREATE INDEX idx_pur_order_line_company_book_order
    ON pur_order_line (company_id, account_book_id, order_id, line_no);

CREATE INDEX idx_pur_receipt_line_company_book_receipt
    ON pur_receipt_line (company_id, account_book_id, receipt_id, line_no);

CREATE INDEX idx_pur_return_line_company_book_return
    ON pur_return_line (company_id, account_book_id, return_id, line_no);

CREATE INDEX idx_sal_order_line_company_book_order
    ON sal_order_line (company_id, account_book_id, order_id, line_no);

CREATE INDEX idx_sal_delivery_line_company_book_delivery
    ON sal_delivery_line (company_id, account_book_id, delivery_id, line_no);

CREATE INDEX idx_sal_return_line_company_book_return
    ON sal_return_line (company_id, account_book_id, return_id, line_no);
