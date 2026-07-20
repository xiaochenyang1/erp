-- ============================================================
-- V105: 补充缺失的查询索引
-- ============================================================

-- 1. wf_approval_task: 审批人查询（仪表盘"待我审批"）
CREATE INDEX idx_wf_task_company_book_approver_status
    ON wf_approval_task (company_id, account_book_id, approver_user_id, status);

-- 清理 V20 遗留的非租户索引（已被 V49/V50 的租户索引取代）
DROP INDEX idx_wf_task_instance_status ON wf_approval_task;
DROP INDEX idx_wf_task_source_status ON wf_approval_task;

-- 2. pur_order: approval_status 筛选
CREATE INDEX idx_pur_order_company_book_approval_status
    ON pur_order (company_id, account_book_id, approval_status);

-- 3. sal_order: approval_status / delivery_status 筛选
CREATE INDEX idx_sal_order_company_book_approval_status
    ON sal_order (company_id, account_book_id, approval_status);

CREATE INDEX idx_sal_order_company_book_delivery_status
    ON sal_order (company_id, account_book_id, delivery_status);

-- 4. pur_receipt: 列表查询复合索引
CREATE INDEX idx_pur_receipt_company_book_deleted_status_date
    ON pur_receipt (company_id, account_book_id, deleted_flag, status, receipt_date);

-- 5. pur_return: 列表查询复合索引
CREATE INDEX idx_pur_return_company_book_deleted_status
    ON pur_return (company_id, account_book_id, deleted_flag, status);

-- 6. DataScopeService 行级安全: created_by 索引
CREATE INDEX idx_pur_order_company_book_created_by
    ON pur_order (company_id, account_book_id, created_by);

CREATE INDEX idx_sal_order_company_book_created_by
    ON sal_order (company_id, account_book_id, created_by);

CREATE INDEX idx_prd_order_company_book_created_by
    ON prd_order (company_id, account_book_id, created_by);

-- 7. prd_order: product_id / bom_id 筛选
CREATE INDEX idx_prd_order_company_book_product
    ON prd_order (company_id, account_book_id, product_id);

CREATE INDEX idx_prd_order_company_book_bom
    ON prd_order (company_id, account_book_id, bom_id);

-- 8. sys_user: 用户列表租户复合索引
CREATE INDEX idx_sys_user_company_book_deleted
    ON sys_user (company_id, account_book_id, deleted_flag);
