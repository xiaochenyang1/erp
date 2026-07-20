DROP INDEX idx_md_product_company_deleted_status_code ON md_product;
CREATE INDEX idx_md_product_company_book_deleted_status_code
    ON md_product (company_id, account_book_id, deleted_flag, status, product_code);

DROP INDEX idx_md_customer_company_deleted_status_code ON md_customer;
CREATE INDEX idx_md_customer_company_book_deleted_status_code
    ON md_customer (company_id, account_book_id, deleted_flag, status, customer_code);

DROP INDEX idx_md_supplier_company_deleted_status_code ON md_supplier;
CREATE INDEX idx_md_supplier_company_book_deleted_status_code
    ON md_supplier (company_id, account_book_id, deleted_flag, status, supplier_code);

DROP INDEX idx_md_warehouse_company_deleted_status_code ON md_warehouse;
CREATE INDEX idx_md_warehouse_company_book_deleted_status_code
    ON md_warehouse (company_id, account_book_id, deleted_flag, status, warehouse_code);

DROP INDEX idx_md_warehouse_company_dept_id ON md_warehouse;
CREATE INDEX idx_md_warehouse_company_book_dept_id
    ON md_warehouse (company_id, account_book_id, dept_id);

DROP INDEX idx_md_warehouse_company_manager_user_id ON md_warehouse;
CREATE INDEX idx_md_warehouse_company_book_manager_user_id
    ON md_warehouse (company_id, account_book_id, manager_user_id);

DROP INDEX idx_sys_import_job_company_type_status ON sys_import_job;
CREATE INDEX idx_sys_import_job_company_book_type_status
    ON sys_import_job (company_id, account_book_id, import_type, status, created_time);

DROP INDEX idx_sys_import_job_company_created ON sys_import_job;
CREATE INDEX idx_sys_import_job_company_book_created
    ON sys_import_job (company_id, account_book_id, created_time);

DROP INDEX idx_sys_import_job_row_company_job ON sys_import_job_row;
CREATE INDEX idx_sys_import_job_row_company_book_job
    ON sys_import_job_row (company_id, account_book_id, job_id);

DROP INDEX idx_sys_attachment_created_time ON sys_attachment;
CREATE INDEX idx_sys_attachment_company_book_created_time
    ON sys_attachment (company_id, account_book_id, created_time);
