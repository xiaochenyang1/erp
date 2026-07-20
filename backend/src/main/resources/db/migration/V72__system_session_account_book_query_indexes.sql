DROP INDEX idx_sys_refresh_token_company_status ON sys_refresh_token;
DROP INDEX idx_sys_refresh_token_account_book ON sys_refresh_token;

CREATE INDEX idx_sys_refresh_token_company_book_status
    ON sys_refresh_token (company_id, account_book_id, status, issued_at, id);

CREATE INDEX idx_sys_refresh_token_company_book_user_status
    ON sys_refresh_token (company_id, account_book_id, user_id, status);
