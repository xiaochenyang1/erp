-- V134: 销售发货物流轻量字段
ALTER TABLE sal_delivery ADD COLUMN carrier_name VARCHAR(128) NULL;
ALTER TABLE sal_delivery ADD COLUMN tracking_no VARCHAR(128) NULL;
CREATE INDEX idx_sal_delivery_company_book_tracking
    ON sal_delivery (company_id, account_book_id, tracking_no);
