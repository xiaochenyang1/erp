ALTER TABLE sys_login_log ADD COLUMN company_id BIGINT;
ALTER TABLE sys_login_log ADD COLUMN account_book_id BIGINT;

ALTER TABLE sys_operation_log ADD COLUMN company_id BIGINT;
ALTER TABLE sys_operation_log ADD COLUMN account_book_id BIGINT;

ALTER TABLE sys_audit_log ADD COLUMN company_id BIGINT;
ALTER TABLE sys_audit_log ADD COLUMN account_book_id BIGINT;

UPDATE sys_login_log l
SET l.company_id = (SELECT u.company_id FROM sys_user u WHERE u.id = l.user_id),
    l.account_book_id = (SELECT u.account_book_id FROM sys_user u WHERE u.id = l.user_id)
WHERE l.user_id IS NOT NULL
  AND l.company_id IS NULL
  AND EXISTS (SELECT 1 FROM sys_user u WHERE u.id = l.user_id);

UPDATE sys_operation_log l
SET l.company_id = (SELECT u.company_id FROM sys_user u WHERE u.id = l.user_id),
    l.account_book_id = (SELECT u.account_book_id FROM sys_user u WHERE u.id = l.user_id)
WHERE l.user_id IS NOT NULL
  AND l.company_id IS NULL
  AND EXISTS (SELECT 1 FROM sys_user u WHERE u.id = l.user_id);

UPDATE sys_audit_log l
SET l.company_id = (SELECT u.company_id FROM sys_user u WHERE u.id = l.operator_id),
    l.account_book_id = (SELECT u.account_book_id FROM sys_user u WHERE u.id = l.operator_id)
WHERE l.operator_id IS NOT NULL
  AND l.company_id IS NULL
  AND EXISTS (SELECT 1 FROM sys_user u WHERE u.id = l.operator_id);

CREATE INDEX idx_sys_login_log_company_book_time ON sys_login_log (company_id, account_book_id, login_time);
CREATE INDEX idx_sys_operation_log_company_book_time ON sys_operation_log (company_id, account_book_id, operation_time);
CREATE INDEX idx_sys_operation_log_company_book_biz ON sys_operation_log (company_id, account_book_id, biz_no);
CREATE INDEX idx_sys_audit_log_company_book_time ON sys_audit_log (company_id, account_book_id, audit_time);
CREATE INDEX idx_sys_audit_log_company_book_business ON sys_audit_log (company_id, account_book_id, business_type, business_id);
