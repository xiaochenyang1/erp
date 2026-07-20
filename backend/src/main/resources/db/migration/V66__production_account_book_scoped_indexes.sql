DROP INDEX uk_prd_bom_company_bom_no ON prd_bom;
CREATE UNIQUE INDEX uk_prd_bom_company_book_bom_no
    ON prd_bom (company_id, account_book_id, bom_no);

DROP INDEX idx_prd_bom_company_product ON prd_bom;
CREATE INDEX idx_prd_bom_company_book_product
    ON prd_bom (company_id, account_book_id, product_id, status);

DROP INDEX uk_prd_bom_line_company_bom_line ON prd_bom_line;
CREATE UNIQUE INDEX uk_prd_bom_line_company_book_bom_line
    ON prd_bom_line (company_id, account_book_id, bom_id, line_no);

DROP INDEX uk_prd_bom_line_company_bom_material ON prd_bom_line;
CREATE UNIQUE INDEX uk_prd_bom_line_company_book_bom_material
    ON prd_bom_line (company_id, account_book_id, bom_id, material_product_id);

DROP INDEX uk_prd_order_company_order_no ON prd_order;
CREATE UNIQUE INDEX uk_prd_order_company_book_order_no
    ON prd_order (company_id, account_book_id, order_no);

DROP INDEX idx_prd_order_company_status ON prd_order;
CREATE INDEX idx_prd_order_company_book_status
    ON prd_order (company_id, account_book_id, status, planned_start_date);

DROP INDEX idx_prd_order_company_warehouses ON prd_order;
CREATE INDEX idx_prd_order_company_book_warehouses
    ON prd_order (company_id, account_book_id, material_warehouse_id, finished_warehouse_id);

DROP INDEX uk_prd_order_material_company_order_line ON prd_order_material;
CREATE UNIQUE INDEX uk_prd_order_material_company_book_order_line
    ON prd_order_material (company_id, account_book_id, order_id, line_no);

DROP INDEX idx_prd_order_material_company_order ON prd_order_material;
CREATE INDEX idx_prd_order_material_company_book_order
    ON prd_order_material (company_id, account_book_id, order_id);

DROP INDEX uk_prd_issue_company_no ON prd_issue;
CREATE UNIQUE INDEX uk_prd_issue_company_book_no
    ON prd_issue (company_id, account_book_id, issue_no);

DROP INDEX idx_prd_issue_company_order ON prd_issue;
CREATE INDEX idx_prd_issue_company_book_order
    ON prd_issue (company_id, account_book_id, order_id, issue_date);

DROP INDEX idx_prd_issue_line_company_issue ON prd_issue_line;
CREATE INDEX idx_prd_issue_line_company_book_issue
    ON prd_issue_line (company_id, account_book_id, issue_id);

DROP INDEX idx_prd_issue_line_company_material ON prd_issue_line;
CREATE INDEX idx_prd_issue_line_company_book_material
    ON prd_issue_line (company_id, account_book_id, order_material_id);

DROP INDEX uk_prd_completion_company_no ON prd_completion;
CREATE UNIQUE INDEX uk_prd_completion_company_book_no
    ON prd_completion (company_id, account_book_id, completion_no);

DROP INDEX idx_prd_completion_company_order ON prd_completion;
CREATE INDEX idx_prd_completion_company_book_order
    ON prd_completion (company_id, account_book_id, order_id, completion_date);

DROP INDEX uk_prd_return_company_no ON prd_return;
CREATE UNIQUE INDEX uk_prd_return_company_book_no
    ON prd_return (company_id, account_book_id, return_no);

DROP INDEX idx_prd_return_company_order ON prd_return;
CREATE INDEX idx_prd_return_company_book_order
    ON prd_return (company_id, account_book_id, order_id, return_date);

DROP INDEX idx_prd_return_line_company_return ON prd_return_line;
CREATE INDEX idx_prd_return_line_company_book_return
    ON prd_return_line (company_id, account_book_id, return_id);

DROP INDEX idx_prd_return_line_company_material ON prd_return_line;
CREATE INDEX idx_prd_return_line_company_book_material
    ON prd_return_line (company_id, account_book_id, order_material_id);

DROP INDEX uk_prd_completion_reversal_company_no ON prd_completion_reversal;
CREATE UNIQUE INDEX uk_prd_completion_reversal_company_book_no
    ON prd_completion_reversal (company_id, account_book_id, reversal_no);

DROP INDEX idx_prd_completion_reversal_company_order ON prd_completion_reversal;
CREATE INDEX idx_prd_completion_reversal_company_book_order
    ON prd_completion_reversal (company_id, account_book_id, order_id, reversal_date);
