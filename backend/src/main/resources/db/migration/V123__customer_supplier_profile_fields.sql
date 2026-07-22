ALTER TABLE md_customer
    ADD COLUMN customer_type VARCHAR(32) NULL,
    ADD COLUMN email VARCHAR(128) NULL;

UPDATE md_customer
SET customer_type = 'COMPANY'
WHERE customer_type IS NULL;

ALTER TABLE md_supplier
    ADD COLUMN email VARCHAR(128) NULL,
    ADD COLUMN credit_period INT NULL;

UPDATE md_customer SET status = 'INACTIVE' WHERE status = 'DISABLED';
UPDATE md_supplier SET status = 'INACTIVE' WHERE status = 'DISABLED';
UPDATE md_product SET status = 'INACTIVE' WHERE status = 'DISABLED';
UPDATE md_warehouse SET status = 'INACTIVE' WHERE status = 'DISABLED';
