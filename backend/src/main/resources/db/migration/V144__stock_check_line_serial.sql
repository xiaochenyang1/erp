-- V144: 盘点明细支持序列号，供差异调整过账传递
ALTER TABLE inv_stock_check_line ADD COLUMN serial_nos VARCHAR(2000) NULL;
