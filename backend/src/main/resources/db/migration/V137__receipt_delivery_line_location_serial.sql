-- V137: 收货/发货明细支持库位与序列号
ALTER TABLE pur_receipt_line ADD COLUMN location_id BIGINT NULL;
ALTER TABLE pur_receipt_line ADD COLUMN serial_nos VARCHAR(1000) NULL;
ALTER TABLE sal_delivery_line ADD COLUMN location_id BIGINT NULL;
ALTER TABLE sal_delivery_line ADD COLUMN serial_nos VARCHAR(1000) NULL;
CREATE INDEX idx_pur_receipt_line_location ON pur_receipt_line (company_id, account_book_id, location_id);
CREATE INDEX idx_sal_delivery_line_location ON sal_delivery_line (company_id, account_book_id, location_id);
