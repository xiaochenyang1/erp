ALTER TABLE md_product
    ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE md_product
    ADD COLUMN account_book_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE md_customer
    ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE md_customer
    ADD COLUMN account_book_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE md_supplier
    ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE md_supplier
    ADD COLUMN account_book_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE md_warehouse
    ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE md_warehouse
    ADD COLUMN account_book_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE inv_balance
    ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE inv_balance
    ADD COLUMN account_book_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE inv_txn
    ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE inv_txn
    ADD COLUMN account_book_id BIGINT NOT NULL DEFAULT 1;

DROP INDEX uk_md_product_product_code ON md_product;
DROP INDEX uk_md_customer_customer_code ON md_customer;
DROP INDEX uk_md_supplier_supplier_code ON md_supplier;
DROP INDEX uk_md_warehouse_warehouse_code ON md_warehouse;
DROP INDEX uk_inv_balance_warehouse_id_product_id ON inv_balance;

CREATE UNIQUE INDEX uk_md_product_company_product_code ON md_product (company_id, product_code);
CREATE UNIQUE INDEX uk_md_customer_company_customer_code ON md_customer (company_id, customer_code);
CREATE UNIQUE INDEX uk_md_supplier_company_supplier_code ON md_supplier (company_id, supplier_code);
CREATE UNIQUE INDEX uk_md_warehouse_company_warehouse_code ON md_warehouse (company_id, warehouse_code);
CREATE UNIQUE INDEX uk_inv_balance_company_warehouse_product ON inv_balance (company_id, warehouse_id, product_id);

CREATE INDEX idx_md_product_company_deleted_status_code ON md_product (company_id, deleted_flag, status, product_code);
CREATE INDEX idx_md_customer_company_deleted_status_code ON md_customer (company_id, deleted_flag, status, customer_code);
CREATE INDEX idx_md_supplier_company_deleted_status_code ON md_supplier (company_id, deleted_flag, status, supplier_code);
CREATE INDEX idx_md_warehouse_company_deleted_status_code ON md_warehouse (company_id, deleted_flag, status, warehouse_code);
CREATE INDEX idx_md_warehouse_company_dept_id ON md_warehouse (company_id, dept_id);
CREATE INDEX idx_md_warehouse_company_manager_user_id ON md_warehouse (company_id, manager_user_id);

CREATE INDEX idx_inv_balance_company_product ON inv_balance (company_id, product_id);
CREATE INDEX idx_inv_txn_company_biz_no ON inv_txn (company_id, biz_no);
CREATE INDEX idx_inv_txn_company_warehouse_product ON inv_txn (company_id, warehouse_id, product_id);
CREATE INDEX idx_inv_txn_company_occurred_time ON inv_txn (company_id, occurred_time);
