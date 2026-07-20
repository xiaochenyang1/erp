ALTER TABLE sys_dept
    ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE sys_dept
    ADD COLUMN account_book_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE sys_post
    ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE sys_post
    ADD COLUMN account_book_id BIGINT NOT NULL DEFAULT 1;

DROP INDEX uk_sys_dept_dept_code ON sys_dept;
DROP INDEX uk_sys_post_post_code ON sys_post;

CREATE UNIQUE INDEX uk_sys_dept_company_dept_code ON sys_dept (company_id, dept_code);
CREATE UNIQUE INDEX uk_sys_post_company_post_code ON sys_post (company_id, post_code);
CREATE INDEX idx_sys_dept_company_parent_sort ON sys_dept (company_id, parent_id, sort_no, id);
CREATE INDEX idx_sys_post_company_dept_code ON sys_post (company_id, dept_id, post_code);
