ALTER TABLE md_product
    ADD COLUMN barcode VARCHAR(128) NULL;

CREATE UNIQUE INDEX uk_md_product_company_book_barcode
    ON md_product (company_id, account_book_id, barcode);
