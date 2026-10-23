ALTER TABLE sal_delivery ADD COLUMN delivered_by VARCHAR(128) NULL;
ALTER TABLE sal_delivery ADD COLUMN delivered_time TIMESTAMP NULL;
ALTER TABLE sal_delivery ADD COLUMN delivery_proof_attachment_id BIGINT NULL;
CREATE INDEX idx_sal_delivery_company_book_delivered_time ON sal_delivery (company_id, account_book_id, delivered_time);
