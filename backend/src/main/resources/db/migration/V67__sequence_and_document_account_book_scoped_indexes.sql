ALTER TABLE sys_sequence_rule
    ADD COLUMN account_book_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE sys_sequence_counter
    ADD COLUMN account_book_id BIGINT NOT NULL DEFAULT 1;

DROP INDEX uk_sys_sequence_rule_company_biz_type ON sys_sequence_rule;
CREATE UNIQUE INDEX uk_sys_sequence_rule_company_book_biz_type
    ON sys_sequence_rule (company_id, account_book_id, biz_type);

DROP INDEX uk_sys_sequence_counter_company_biz_period ON sys_sequence_counter;
CREATE UNIQUE INDEX uk_sys_sequence_counter_company_book_biz_period
    ON sys_sequence_counter (company_id, account_book_id, biz_type, period_key);

DROP INDEX idx_sys_sequence_counter_company_biz ON sys_sequence_counter;
CREATE INDEX idx_sys_sequence_counter_company_book_biz
    ON sys_sequence_counter (company_id, account_book_id, biz_type);

DROP INDEX uk_pur_order_company_order_no ON pur_order;
CREATE UNIQUE INDEX uk_pur_order_company_book_order_no
    ON pur_order (company_id, account_book_id, order_no);

DROP INDEX uk_pur_receipt_company_receipt_no ON pur_receipt;
CREATE UNIQUE INDEX uk_pur_receipt_company_book_receipt_no
    ON pur_receipt (company_id, account_book_id, receipt_no);

DROP INDEX uk_pur_return_company_return_no ON pur_return;
CREATE UNIQUE INDEX uk_pur_return_company_book_return_no
    ON pur_return (company_id, account_book_id, return_no);

DROP INDEX uk_sal_order_company_order_no ON sal_order;
CREATE UNIQUE INDEX uk_sal_order_company_book_order_no
    ON sal_order (company_id, account_book_id, order_no);

DROP INDEX uk_sal_delivery_company_delivery_no ON sal_delivery;
CREATE UNIQUE INDEX uk_sal_delivery_company_book_delivery_no
    ON sal_delivery (company_id, account_book_id, delivery_no);

DROP INDEX uk_sal_return_company_return_no ON sal_return;
CREATE UNIQUE INDEX uk_sal_return_company_book_return_no
    ON sal_return (company_id, account_book_id, return_no);

DROP INDEX uk_inv_adjustment_company_adjustment_no ON inv_adjustment;
CREATE UNIQUE INDEX uk_inv_adjustment_company_book_adjustment_no
    ON inv_adjustment (company_id, account_book_id, adjustment_no);

DROP INDEX uk_inv_stock_check_company_check_no ON inv_stock_check;
CREATE UNIQUE INDEX uk_inv_stock_check_company_book_check_no
    ON inv_stock_check (company_id, account_book_id, check_no);

DROP INDEX uk_inv_transfer_company_transfer_no ON inv_transfer;
CREATE UNIQUE INDEX uk_inv_transfer_company_book_transfer_no
    ON inv_transfer (company_id, account_book_id, transfer_no);

DROP INDEX uk_fin_payable_company_payable_no ON fin_payable;
CREATE UNIQUE INDEX uk_fin_payable_company_book_payable_no
    ON fin_payable (company_id, account_book_id, payable_no);

DROP INDEX uk_fin_payable_company_source ON fin_payable;
CREATE UNIQUE INDEX uk_fin_payable_company_book_source
    ON fin_payable (company_id, account_book_id, source_type, source_id);

DROP INDEX uk_fin_payment_company_payment_no ON fin_payment;
CREATE UNIQUE INDEX uk_fin_payment_company_book_payment_no
    ON fin_payment (company_id, account_book_id, payment_no);

DROP INDEX uk_fin_receivable_company_receivable_no ON fin_receivable;
CREATE UNIQUE INDEX uk_fin_receivable_company_book_receivable_no
    ON fin_receivable (company_id, account_book_id, receivable_no);

DROP INDEX uk_fin_receivable_company_source ON fin_receivable;
CREATE UNIQUE INDEX uk_fin_receivable_company_book_source
    ON fin_receivable (company_id, account_book_id, source_type, source_id);

DROP INDEX uk_fin_receipt_company_receipt_no ON fin_receipt;
CREATE UNIQUE INDEX uk_fin_receipt_company_book_receipt_no
    ON fin_receipt (company_id, account_book_id, receipt_no);

DROP INDEX uk_fin_voucher_company_voucher_no ON fin_voucher;
CREATE UNIQUE INDEX uk_fin_voucher_company_book_voucher_no
    ON fin_voucher (company_id, account_book_id, voucher_no);

DROP INDEX uk_fin_voucher_company_source ON fin_voucher;
CREATE UNIQUE INDEX uk_fin_voucher_company_book_source
    ON fin_voucher (company_id, account_book_id, source_type, source_id);

DROP INDEX uk_inv_alert_rule_company_product_warehouse ON inv_alert_rule;
CREATE UNIQUE INDEX uk_inv_alert_rule_company_book_product_warehouse
    ON inv_alert_rule (company_id, account_book_id, product_id, warehouse_id);
