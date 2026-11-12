-- V178: seed the default tax subject referenced by the V175/V176 report mappings.
--
-- The posting service can create 222101 lazily, but a fresh installation must have
-- every default mapping target before any tax voucher exists. Other account books
-- continue to manage their own chart of accounts and report mappings explicitly.

INSERT INTO fin_account_subject
(id, company_id, account_book_id, subject_code, subject_name, parent_id, subject_type, balance_direction,
 status, deleted_flag, remark, created_by, updated_by, version)
SELECT candidate.next_id,
       1, 1, '222101', '应交税费-进项税额', NULL, 'LIABILITY', 'DEBIT',
       'ACTIVE', 0, '财务报表和现金流量表默认税费映射科目', 0, 0, 0
FROM (
    SELECT GREATEST(COALESCE(MAX(id), 0), 910014) + 1 AS next_id
    FROM fin_account_subject
) AS candidate
WHERE NOT EXISTS (
    SELECT 1
    FROM fin_account_subject
    WHERE company_id = 1
      AND account_book_id = 1
      AND subject_code = '222101'
);
