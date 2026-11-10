-- V149: 合同与销售/采购订单执行关联
ALTER TABLE sal_order ADD COLUMN contract_id BIGINT NULL;
ALTER TABLE sal_order_line ADD COLUMN contract_line_id BIGINT NULL;
ALTER TABLE pur_order ADD COLUMN contract_id BIGINT NULL;
ALTER TABLE pur_order_line ADD COLUMN contract_line_id BIGINT NULL;

CREATE INDEX idx_sal_order_contract ON sal_order (company_id, account_book_id, contract_id, status, deleted_flag);
CREATE INDEX idx_sal_order_line_contract ON sal_order_line (company_id, account_book_id, contract_line_id, order_id);
CREATE INDEX idx_pur_order_contract ON pur_order (company_id, account_book_id, contract_id, status, deleted_flag);
CREATE INDEX idx_pur_order_line_contract ON pur_order_line (company_id, account_book_id, contract_line_id, order_id);
