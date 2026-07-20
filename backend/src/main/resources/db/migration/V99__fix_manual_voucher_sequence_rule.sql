-- V99: 修复手工凭证单号规则缺失。
-- 缺陷:V86 试图用 sys_sequence_rule.id=2013 播种 FIN_MANUAL_VOUCHER,但该主键早在
-- V58 已被 FIN_BANK_STATEMENT 占用;V86 的 ON DUPLICATE KEY UPDATE 不改写 biz_type,
-- 导致 FIN_MANUAL_VOUCHER 规则在所有环境从未生成,手工凭证创建在 nextNumber 处必然报
-- "手工凭证编号规则不存在"。此处用未占用主键 2014 补种,唯一键为
-- (company_id, account_book_id, biz_type),幂等。

INSERT INTO sys_sequence_rule
(id, company_id, account_book_id, biz_type, prefix, date_pattern, seq_length, current_value,
 status, created_by, updated_by, version)
VALUES
    (2014, 1, 1, 'FIN_MANUAL_VOUCHER', 'MV', 'yyyyMMdd', 4, 0, 'ACTIVE', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    prefix = VALUES(prefix),
    date_pattern = VALUES(date_pattern),
    seq_length = VALUES(seq_length),
    current_value = GREATEST(current_value, VALUES(current_value)),
    status = VALUES(status);
