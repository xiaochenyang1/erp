INSERT INTO fin_account_subject
(id, company_id, account_book_id, subject_code, subject_name, parent_id, subject_type, balance_direction,
 status, deleted_flag, remark, created_by, updated_by, version)
VALUES
    (910009, 1, 1, '5001', '生产成本', NULL, 'ASSET', 'DEBIT', 'ACTIVE', 0, '生产领料与完工结转科目', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    subject_name = VALUES(subject_name),
    subject_type = VALUES(subject_type),
    balance_direction = VALUES(balance_direction),
    status = VALUES(status),
    deleted_flag = VALUES(deleted_flag),
    remark = VALUES(remark),
    updated_by = VALUES(updated_by);
