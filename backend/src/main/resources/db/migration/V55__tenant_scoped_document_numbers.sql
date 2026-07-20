ALTER TABLE sys_sequence_rule
    ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1;

DROP INDEX uk_sys_sequence_rule_biz_type ON sys_sequence_rule;
CREATE UNIQUE INDEX uk_sys_sequence_rule_company_biz_type
    ON sys_sequence_rule (company_id, biz_type);

DROP INDEX uk_pur_order_order_no ON pur_order;
CREATE UNIQUE INDEX uk_pur_order_company_order_no
    ON pur_order (company_id, order_no);

DROP INDEX uk_pur_receipt_receipt_no ON pur_receipt;
CREATE UNIQUE INDEX uk_pur_receipt_company_receipt_no
    ON pur_receipt (company_id, receipt_no);

DROP INDEX uk_pur_return_return_no ON pur_return;
CREATE UNIQUE INDEX uk_pur_return_company_return_no
    ON pur_return (company_id, return_no);

DROP INDEX uk_sal_order_order_no ON sal_order;
CREATE UNIQUE INDEX uk_sal_order_company_order_no
    ON sal_order (company_id, order_no);

DROP INDEX uk_sal_delivery_delivery_no ON sal_delivery;
CREATE UNIQUE INDEX uk_sal_delivery_company_delivery_no
    ON sal_delivery (company_id, delivery_no);

DROP INDEX uk_sal_return_return_no ON sal_return;
CREATE UNIQUE INDEX uk_sal_return_company_return_no
    ON sal_return (company_id, return_no);

DROP INDEX uk_inv_adjustment_adjustment_no ON inv_adjustment;
CREATE UNIQUE INDEX uk_inv_adjustment_company_adjustment_no
    ON inv_adjustment (company_id, adjustment_no);

DROP INDEX uk_inv_stock_check_check_no ON inv_stock_check;
CREATE UNIQUE INDEX uk_inv_stock_check_company_check_no
    ON inv_stock_check (company_id, check_no);

DROP INDEX uk_inv_transfer_transfer_no ON inv_transfer;
CREATE UNIQUE INDEX uk_inv_transfer_company_transfer_no
    ON inv_transfer (company_id, transfer_no);

DROP INDEX uk_fin_payable_payable_no ON fin_payable;
CREATE UNIQUE INDEX uk_fin_payable_company_payable_no
    ON fin_payable (company_id, payable_no);

DROP INDEX uk_fin_payable_source ON fin_payable;
CREATE UNIQUE INDEX uk_fin_payable_company_source
    ON fin_payable (company_id, source_type, source_id);

DROP INDEX uk_fin_payment_payment_no ON fin_payment;
CREATE UNIQUE INDEX uk_fin_payment_company_payment_no
    ON fin_payment (company_id, payment_no);

DROP INDEX uk_fin_receivable_receivable_no ON fin_receivable;
CREATE UNIQUE INDEX uk_fin_receivable_company_receivable_no
    ON fin_receivable (company_id, receivable_no);

DROP INDEX uk_fin_receivable_source ON fin_receivable;
CREATE UNIQUE INDEX uk_fin_receivable_company_source
    ON fin_receivable (company_id, source_type, source_id);

DROP INDEX uk_fin_receipt_receipt_no ON fin_receipt;
CREATE UNIQUE INDEX uk_fin_receipt_company_receipt_no
    ON fin_receipt (company_id, receipt_no);

DROP INDEX uk_fin_voucher_voucher_no ON fin_voucher;
CREATE UNIQUE INDEX uk_fin_voucher_company_voucher_no
    ON fin_voucher (company_id, voucher_no);

DROP INDEX uk_fin_voucher_source ON fin_voucher;
CREATE UNIQUE INDEX uk_fin_voucher_company_source
    ON fin_voucher (company_id, source_type, source_id);

DROP INDEX uk_inv_alert_rule_product_warehouse ON inv_alert_rule;
CREATE UNIQUE INDEX uk_inv_alert_rule_company_product_warehouse
    ON inv_alert_rule (company_id, product_id, warehouse_id);

DROP INDEX uk_wf_instance_active_source ON wf_approval_instance;
CREATE INDEX idx_wf_instance_company_source_status
    ON wf_approval_instance (company_id, account_book_id, business_type, business_id, status);
