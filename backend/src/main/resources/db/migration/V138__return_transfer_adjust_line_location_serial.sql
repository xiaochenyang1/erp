-- V138: 退货/调拨/调整明细支持库位与序列号
ALTER TABLE pur_return_line ADD COLUMN location_id BIGINT NULL;
ALTER TABLE pur_return_line ADD COLUMN serial_nos VARCHAR(1000) NULL;
ALTER TABLE sal_return_line ADD COLUMN location_id BIGINT NULL;
ALTER TABLE sal_return_line ADD COLUMN serial_nos VARCHAR(1000) NULL;
ALTER TABLE inv_transfer_line ADD COLUMN from_location_id BIGINT NULL;
ALTER TABLE inv_transfer_line ADD COLUMN to_location_id BIGINT NULL;
ALTER TABLE inv_transfer_line ADD COLUMN serial_nos VARCHAR(1000) NULL;
ALTER TABLE inv_adjustment_line ADD COLUMN location_id BIGINT NULL;
ALTER TABLE inv_adjustment_line ADD COLUMN serial_nos VARCHAR(1000) NULL;

CREATE INDEX idx_pur_return_line_location ON pur_return_line (company_id, account_book_id, location_id);
CREATE INDEX idx_sal_return_line_location ON sal_return_line (company_id, account_book_id, location_id);
CREATE INDEX idx_inv_transfer_line_from_location ON inv_transfer_line (company_id, account_book_id, from_location_id);
CREATE INDEX idx_inv_transfer_line_to_location ON inv_transfer_line (company_id, account_book_id, to_location_id);
CREATE INDEX idx_inv_adjustment_line_location ON inv_adjustment_line (company_id, account_book_id, location_id);
