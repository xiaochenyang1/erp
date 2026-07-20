-- 为采购订单 / 销售订单两张交易主表补充多租户复合索引。
--
-- 背景：pur_order / sal_order 的列表与报表查询恒带 (company_id, account_book_id, deleted_flag)
-- 过滤，再叠加 status / order_date / supplier_id / customer_id 条件（见
-- PurchaseOrderService、SalesOrderService、ReportQueryService）。但 V13 / V17 建的
-- idx_*_status / idx_*_order_date / idx_*_supplier_id / idx_*_customer_id 均为单列索引，
-- 在「先按租户过滤」的场景下选择度差，优化器往往退化为按租户扫描后回表过滤。
--
-- 对齐 V71 已为 masterdata / import / attachment 表建立的
-- (company_id, account_book_id, ...) 复合索引风格，这里用租户前缀的复合索引替换退化的单列索引。

-- 采购订单
DROP INDEX idx_pur_order_status ON pur_order;
DROP INDEX idx_pur_order_order_date ON pur_order;
DROP INDEX idx_pur_order_supplier_id ON pur_order;

CREATE INDEX idx_pur_order_company_book_deleted_status_date
    ON pur_order (company_id, account_book_id, deleted_flag, status, order_date);
CREATE INDEX idx_pur_order_company_book_supplier
    ON pur_order (company_id, account_book_id, supplier_id);

-- 销售订单
DROP INDEX idx_sal_order_status ON sal_order;
DROP INDEX idx_sal_order_order_date ON sal_order;
DROP INDEX idx_sal_order_customer_id ON sal_order;

CREATE INDEX idx_sal_order_company_book_deleted_status_date
    ON sal_order (company_id, account_book_id, deleted_flag, status, order_date);
CREATE INDEX idx_sal_order_company_book_customer
    ON sal_order (company_id, account_book_id, customer_id);
