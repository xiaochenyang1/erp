-- V142: 生产领料/完工/退料支持库位与序列号
ALTER TABLE prd_issue_line ADD COLUMN location_id BIGINT NULL;
ALTER TABLE prd_issue_line ADD COLUMN serial_nos VARCHAR(1000) NULL;
ALTER TABLE prd_completion ADD COLUMN location_id BIGINT NULL;
ALTER TABLE prd_completion ADD COLUMN serial_nos VARCHAR(1000) NULL;
ALTER TABLE prd_return_line ADD COLUMN location_id BIGINT NULL;
ALTER TABLE prd_return_line ADD COLUMN serial_nos VARCHAR(1000) NULL;
CREATE INDEX idx_prd_issue_line_location ON prd_issue_line (company_id, account_book_id, location_id);
CREATE INDEX idx_prd_completion_location ON prd_completion (company_id, account_book_id, location_id);
CREATE INDEX idx_prd_return_line_location ON prd_return_line (company_id, account_book_id, location_id);
