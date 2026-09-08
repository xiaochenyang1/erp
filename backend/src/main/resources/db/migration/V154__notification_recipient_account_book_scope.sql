-- V154: 将通知 recipient 纳入账套边界。
--
-- 先以默认账套值完成 expand，再从通知主表回填历史记录。若存在孤儿
-- recipient 或 company/account_book 不一致的历史数据，下面的复合外键会
-- 让迁移失败并暴露数据问题，而不是静默把记录归入错误账套。
ALTER TABLE sys_notification_recipient
    ADD COLUMN account_book_id BIGINT NOT NULL DEFAULT 1;

UPDATE sys_notification_recipient
SET account_book_id = (
    SELECT n.account_book_id
    FROM sys_notification n
    WHERE n.id = sys_notification_recipient.notification_id
      AND n.company_id = sys_notification_recipient.company_id
)
WHERE EXISTS (
    SELECT 1
    FROM sys_notification n
    WHERE n.id = sys_notification_recipient.notification_id
      AND n.company_id = sys_notification_recipient.company_id
);

-- MySQL 需要父表存在复合唯一索引才能创建复合外键。
CREATE UNIQUE INDEX uk_sys_notification_company_book_id
    ON sys_notification (company_id, account_book_id, id);

ALTER TABLE sys_notification_recipient
    ADD CONSTRAINT fk_sys_notification_recipient_notification_scope
    FOREIGN KEY (company_id, account_book_id, notification_id)
    REFERENCES sys_notification (company_id, account_book_id, id);

CREATE INDEX idx_sys_notification_recipient_user_book
    ON sys_notification_recipient (company_id, account_book_id, recipient_user_id, status, read_flag, created_time);
CREATE INDEX idx_sys_notification_recipient_notification_book
    ON sys_notification_recipient (company_id, account_book_id, notification_id, status);
