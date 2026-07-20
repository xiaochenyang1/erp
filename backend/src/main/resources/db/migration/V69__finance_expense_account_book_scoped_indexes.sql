DROP INDEX idx_fin_account_subject_company_parent ON fin_account_subject;
CREATE INDEX idx_fin_account_subject_company_book_parent
    ON fin_account_subject (company_id, account_book_id, parent_id);

DROP INDEX uk_fin_expense_company_no ON fin_expense;
CREATE UNIQUE INDEX uk_fin_expense_company_book_no
    ON fin_expense (company_id, account_book_id, expense_no);

DROP INDEX idx_fin_expense_company_date ON fin_expense;
CREATE INDEX idx_fin_expense_company_book_date
    ON fin_expense (company_id, account_book_id, expense_date);

DROP INDEX idx_fin_voucher_entry_company_subject ON fin_voucher_entry;
CREATE INDEX idx_fin_voucher_entry_company_book_subject
    ON fin_voucher_entry (company_id, account_book_id, subject_code, biz_date);

DROP INDEX idx_fin_voucher_entry_company_voucher ON fin_voucher_entry;
CREATE INDEX idx_fin_voucher_entry_company_book_voucher
    ON fin_voucher_entry (company_id, account_book_id, voucher_id);
