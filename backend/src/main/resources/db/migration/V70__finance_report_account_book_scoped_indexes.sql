DROP INDEX idx_fin_payable_report_date ON fin_payable;
CREATE INDEX idx_fin_payable_report_company_book_date
    ON fin_payable (company_id, account_book_id, deleted_flag, biz_date, id);

DROP INDEX idx_fin_payable_report_supplier_date ON fin_payable;
CREATE INDEX idx_fin_payable_report_company_book_supplier_date
    ON fin_payable (company_id, account_book_id, deleted_flag, supplier_id, biz_date, id);

DROP INDEX idx_fin_payable_report_status_date ON fin_payable;
CREATE INDEX idx_fin_payable_report_company_book_status_date
    ON fin_payable (company_id, account_book_id, deleted_flag, status, biz_date, id);

DROP INDEX idx_fin_receivable_report_date ON fin_receivable;
CREATE INDEX idx_fin_receivable_report_company_book_date
    ON fin_receivable (company_id, account_book_id, deleted_flag, biz_date, id);

DROP INDEX idx_fin_receivable_report_customer_date ON fin_receivable;
CREATE INDEX idx_fin_receivable_report_company_book_customer_date
    ON fin_receivable (company_id, account_book_id, deleted_flag, customer_id, biz_date, id);

DROP INDEX idx_fin_receivable_report_status_date ON fin_receivable;
CREATE INDEX idx_fin_receivable_report_company_book_status_date
    ON fin_receivable (company_id, account_book_id, deleted_flag, status, biz_date, id);
