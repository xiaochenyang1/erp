-- V164: 汇兑损益科目种子 (FX gain/loss subject seed for realized FX on settlement).
-- 6061 财务费用-汇兑损益, balance DEBIT (losses debit expense, gains credit).
-- Seed only for demo book 1/1 like V153; other books use defaultSubject fallback.
-- Next id after V153's 2203: 910010.

INSERT INTO fin_account_subject
(id, company_id, account_book_id, subject_code, subject_name, parent_id, subject_type, balance_direction,
 status, deleted_flag, remark, created_by, updated_by, version)
SELECT candidate.next_id,
       1, 1, '6061', '财务费用-汇兑损益', NULL, 'EXPENSE', 'DEBIT',
       'ACTIVE', 0, '多币种结算产生的汇兑损益', 0, 0, 0
FROM (
    SELECT GREATEST(COALESCE(MAX(id), 0), 910010) + 1 AS next_id
    FROM fin_account_subject
) AS candidate
WHERE NOT EXISTS (
    SELECT 1
    FROM fin_account_subject
    WHERE company_id = 1
      AND account_book_id = 1
      AND subject_code = '6061'
);
