-- V143: 采购/销售订单行支持辅单位数量与换算
ALTER TABLE pur_order_line ADD COLUMN aux_qty DECIMAL(18, 4) NULL;
ALTER TABLE pur_order_line ADD COLUMN aux_unit_name VARCHAR(32) NULL;
ALTER TABLE pur_order_line ADD COLUMN conversion_factor DECIMAL(18, 6) NULL;

ALTER TABLE sal_order_line ADD COLUMN aux_qty DECIMAL(18, 4) NULL;
ALTER TABLE sal_order_line ADD COLUMN aux_unit_name VARCHAR(32) NULL;
ALTER TABLE sal_order_line ADD COLUMN conversion_factor DECIMAL(18, 6) NULL;
