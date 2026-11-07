-- 收付款单过账凭证所需科目：预收账款、预付账款。
-- 收付款单原先只核销应收/应付子账，不生成凭证，总账的 1122/2202 永不冲减、1002 从不发生额。
-- 补齐凭证后，收付款总额超出核销额的部分需要单独入预收/预付才能借贷平衡。
-- 其他账套缺失时由 FinanceVoucherPersistenceService#defaultSubject 按需补建，这里只补演示账套。
-- 仅在业务唯一键缺失时补种；已有科目（包括停用、软删或管理员自定义名称）保持不变。
-- 主键从全表最大值后动态取值，避免固定 seed id 与历史数据碰撞时改写无关科目。
INSERT INTO fin_account_subject
(id, company_id, account_book_id, subject_code, subject_name, parent_id, subject_type, balance_direction,
 status, deleted_flag, remark, created_by, updated_by, version)
SELECT candidate.next_id,
       1, 1, '1123', '预付账款', NULL, 'ASSET', 'DEBIT',
       'ACTIVE', 0, '付款超出核销部分的预付科目', 0, 0, 0
FROM (
    SELECT GREATEST(COALESCE(MAX(id), 0), 910009) + 1 AS next_id
    FROM fin_account_subject
) AS candidate
WHERE NOT EXISTS (
    SELECT 1
    FROM fin_account_subject
    WHERE company_id = 1
      AND account_book_id = 1
      AND subject_code = '1123'
);

INSERT INTO fin_account_subject
(id, company_id, account_book_id, subject_code, subject_name, parent_id, subject_type, balance_direction,
 status, deleted_flag, remark, created_by, updated_by, version)
SELECT candidate.next_id,
       1, 1, '2203', '预收账款', NULL, 'LIABILITY', 'CREDIT',
       'ACTIVE', 0, '收款超出核销部分的预收科目', 0, 0, 0
FROM (
    SELECT GREATEST(COALESCE(MAX(id), 0), 910009) + 1 AS next_id
    FROM fin_account_subject
) AS candidate
WHERE NOT EXISTS (
    SELECT 1
    FROM fin_account_subject
    WHERE company_id = 1
      AND account_book_id = 1
      AND subject_code = '2203'
);
