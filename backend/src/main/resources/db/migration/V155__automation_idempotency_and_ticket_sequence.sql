-- V155: 自动提醒/异常工单的数据库幂等键与工单号流水号。
-- dedup_key 保持可空，只有明确需要幂等的自动通知写入该字段；普通通知仍可重复创建。
ALTER TABLE sys_notification
    ADD COLUMN dedup_key VARCHAR(191) NULL;

CREATE UNIQUE INDEX uk_sys_notification_company_book_dedup
    ON sys_notification (company_id, account_book_id, dedup_key);

-- 异常工单沿用统一的租户/账套流水号组件，格式保持 ET-yyyyMMdd-####。
-- 仅在业务唯一键缺失时补种；已有规则（包括 DISABLED 或管理员自定义格式）保持不变。
-- 主键从全表最大值后取一个未占用值，避免固定 seed id 与历史数据冲突时改写别的规则。
INSERT INTO sys_sequence_rule
(id, company_id, account_book_id, biz_type, prefix, date_pattern, seq_length, current_value,
 status, created_by, updated_by, version)
SELECT candidate.next_id,
       1, 1, 'EXCEPTION_TICKET', 'ET-', 'yyyyMMdd-', 4, 0,
       'ACTIVE', 0, 0, 0
FROM (
    SELECT COALESCE(MAX(id), 2036) + 1 AS next_id
    FROM sys_sequence_rule
) AS candidate
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_sequence_rule
    WHERE company_id = 1
      AND account_book_id = 1
      AND biz_type = 'EXCEPTION_TICKET'
);
