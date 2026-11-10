-- Preserve historical plans as algorithm 1; only newly calculated plans have warehouse snapshots.
ALTER TABLE md_product ADD COLUMN mrp_supply_type VARCHAR(16) NOT NULL DEFAULT 'AUTO';
ALTER TABLE md_product ADD COLUMN mrp_material_warehouse_id BIGINT NULL;
ALTER TABLE md_product ADD CONSTRAINT chk_md_product_mrp_supply_type CHECK (mrp_supply_type IN ('AUTO', 'PURCHASE', 'PRODUCTION'));

-- A legacy order with no planned warehouse must be assigned before MRP can consume its supply.
ALTER TABLE pur_order ADD COLUMN planned_warehouse_id BIGINT NULL;
CREATE INDEX idx_pur_order_mrp_warehouse
    ON pur_order (company_id, account_book_id, planned_warehouse_id, status, deleted_flag);

ALTER TABLE inv_mrp_run ADD COLUMN algorithm_version INT NOT NULL DEFAULT 1;
ALTER TABLE inv_mrp_run ADD COLUMN calculation_snapshot LONGTEXT NULL;

ALTER TABLE inv_mrp_run_line ADD COLUMN warehouse_id BIGINT NULL;
ALTER TABLE inv_mrp_run_line ADD COLUMN material_warehouse_id BIGINT NULL;
ALTER TABLE inv_mrp_run_line ADD COLUMN bom_version INT NULL;
ALTER TABLE inv_mrp_run_line ADD COLUMN bom_level INT NULL;
ALTER TABLE inv_mrp_run_line ADD COLUMN calculation_trace LONGTEXT NULL;

CREATE INDEX idx_inv_mrp_line_converted_supply
    ON inv_mrp_run_line (company_id, account_book_id, converted_biz_type, converted_biz_id, status, deleted_flag);
