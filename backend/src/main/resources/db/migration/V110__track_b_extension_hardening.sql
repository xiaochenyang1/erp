-- V110: Track B 扩展收口
-- 1) 补 PURCHASE_INQUIRY 号段 company_id/account_book_id（V108 未显式写入）
-- 2) 种子 notification.webhook.url（空=关闭外发）

UPDATE sys_sequence_rule
SET company_id = COALESCE(company_id, 1),
    account_book_id = COALESCE(account_book_id, 1),
    status = 'ACTIVE',
    updated_by = 0
WHERE id = 2032
   OR biz_type = 'PURCHASE_INQUIRY';

INSERT INTO sys_sequence_rule
(id, company_id, account_book_id, biz_type, prefix, date_pattern, seq_length, current_value,
 status, created_by, updated_by, version)
VALUES
    (2032, 1, 1, 'PURCHASE_INQUIRY', 'RFQ', 'yyyyMMdd', 4, 0, 'ACTIVE', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    company_id = VALUES(company_id),
    account_book_id = VALUES(account_book_id),
    prefix = VALUES(prefix),
    date_pattern = VALUES(date_pattern),
    seq_length = VALUES(seq_length),
    current_value = GREATEST(COALESCE(current_value, 0), VALUES(current_value)),
    status = VALUES(status);

-- 对齐 V27 的 sys_config 列集（无 company/account_book/config_type）
INSERT INTO sys_config
(id, config_code, config_name, config_value, status, deleted_flag, remark, created_by, updated_by, version)
VALUES
    (5401, 'notification.webhook.url', '审批待办 Webhook URL', '', 'ACTIVE', 0,
     '空字符串=关闭外发；填企微/邮件网关等 HTTP 入口后，审批待办创建时异步 POST JSON', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    config_name = VALUES(config_name),
    status = VALUES(status),
    deleted_flag = VALUES(deleted_flag),
    remark = VALUES(remark);
