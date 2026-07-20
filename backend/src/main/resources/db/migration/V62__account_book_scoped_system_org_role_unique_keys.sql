DROP INDEX uk_sys_role_company_role_code ON sys_role;
CREATE UNIQUE INDEX uk_sys_role_company_book_role_code
    ON sys_role (company_id, account_book_id, role_code);

DROP INDEX idx_sys_role_company_deleted_status_code ON sys_role;
CREATE INDEX idx_sys_role_company_book_deleted_status_code
    ON sys_role (company_id, account_book_id, deleted_flag, status, role_code);

DROP INDEX uk_sys_dept_company_dept_code ON sys_dept;
CREATE UNIQUE INDEX uk_sys_dept_company_book_dept_code
    ON sys_dept (company_id, account_book_id, dept_code);

DROP INDEX idx_sys_dept_company_parent_sort ON sys_dept;
CREATE INDEX idx_sys_dept_company_book_parent_sort
    ON sys_dept (company_id, account_book_id, parent_id, sort_no, id);

DROP INDEX uk_sys_post_company_post_code ON sys_post;
CREATE UNIQUE INDEX uk_sys_post_company_book_post_code
    ON sys_post (company_id, account_book_id, post_code);

DROP INDEX idx_sys_post_company_dept_code ON sys_post;
CREATE INDEX idx_sys_post_company_book_dept_code
    ON sys_post (company_id, account_book_id, dept_id, post_code);
