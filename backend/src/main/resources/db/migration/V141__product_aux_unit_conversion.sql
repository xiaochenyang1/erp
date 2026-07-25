-- V141: 商品轻量多单位（库存单位 + 辅单位换算）
ALTER TABLE md_product
    ADD COLUMN aux_unit_name VARCHAR(32) NULL;

ALTER TABLE md_product
    ADD COLUMN conversion_factor DECIMAL(18, 6) NULL;
