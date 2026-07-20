DROP INDEX uk_md_product_company_product_code ON md_product;
CREATE UNIQUE INDEX uk_md_product_company_book_product_code
    ON md_product (company_id, account_book_id, product_code);

DROP INDEX uk_md_customer_company_customer_code ON md_customer;
CREATE UNIQUE INDEX uk_md_customer_company_book_customer_code
    ON md_customer (company_id, account_book_id, customer_code);

DROP INDEX uk_md_supplier_company_supplier_code ON md_supplier;
CREATE UNIQUE INDEX uk_md_supplier_company_book_supplier_code
    ON md_supplier (company_id, account_book_id, supplier_code);

DROP INDEX uk_md_warehouse_company_warehouse_code ON md_warehouse;
CREATE UNIQUE INDEX uk_md_warehouse_company_book_warehouse_code
    ON md_warehouse (company_id, account_book_id, warehouse_code);

DROP INDEX uk_fin_account_subject_company_code ON fin_account_subject;
CREATE UNIQUE INDEX uk_fin_account_subject_company_book_code
    ON fin_account_subject (company_id, account_book_id, subject_code);
