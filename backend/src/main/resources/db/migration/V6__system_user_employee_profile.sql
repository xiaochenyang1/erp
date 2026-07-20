ALTER TABLE sys_user
    ADD COLUMN employee_no VARCHAR(64);

CREATE UNIQUE INDEX uk_sys_user_employee_no ON sys_user (employee_no);
