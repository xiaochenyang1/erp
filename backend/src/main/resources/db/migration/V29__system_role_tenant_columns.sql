ALTER TABLE sys_role
    ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE sys_role
    ADD COLUMN account_book_id BIGINT NOT NULL DEFAULT 1;

DROP INDEX uk_sys_role_role_code ON sys_role;

CREATE UNIQUE INDEX uk_sys_role_company_role_code ON sys_role (company_id, role_code);
CREATE INDEX idx_sys_role_company_deleted_status_code ON sys_role (company_id, deleted_flag, status, role_code);
