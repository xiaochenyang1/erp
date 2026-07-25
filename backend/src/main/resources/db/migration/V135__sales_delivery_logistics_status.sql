-- V135: 销售发货物流状态节点
ALTER TABLE sal_delivery ADD COLUMN logistics_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_SHIP';
UPDATE sal_delivery SET logistics_status = 'PENDING_SHIP' WHERE logistics_status IS NULL OR logistics_status = '';
CREATE INDEX idx_sal_delivery_company_book_logistics
    ON sal_delivery (company_id, account_book_id, logistics_status, status);
