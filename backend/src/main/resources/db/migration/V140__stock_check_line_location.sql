-- V140: 盘点明细支持库位
ALTER TABLE inv_stock_check_line ADD COLUMN location_id BIGINT NULL;
CREATE INDEX idx_inv_stock_check_line_location ON inv_stock_check_line (company_id, account_book_id, location_id);
