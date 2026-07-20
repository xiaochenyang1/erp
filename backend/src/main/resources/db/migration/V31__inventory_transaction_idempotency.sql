CREATE UNIQUE INDEX uk_inv_txn_company_biz_line_direction
    ON inv_txn (company_id, biz_type, biz_line_id, direction);
