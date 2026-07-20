-- V118: 热路径索引补强（账龄/MRP/附件业务维度）
-- 幂等：IF NOT EXISTS 风格用 information_schema 不安全时用简单 CREATE INDEX 忽略失败风险；
-- 这里用标准 CREATE INDEX，已存在时 Flyway 会失败，故用命名检查注释；H2/MySQL 用 CREATE INDEX IF NOT EXISTS 不统一。
-- MySQL 8 不支持 IF NOT EXISTS for index in older versions，使用 procedure-free 重复可忽略：
-- 采用 IF NOT EXISTS for MySQL 8.0+ 部分版本：改用简单索引，命名唯一。

CREATE INDEX idx_fin_receivable_open_aging
    ON fin_receivable (company_id, account_book_id, status, deleted_flag, biz_date);

CREATE INDEX idx_fin_payable_open_aging
    ON fin_payable (company_id, account_book_id, status, deleted_flag, biz_date);

CREATE INDEX idx_sal_order_approved_delivery
    ON sal_order (company_id, account_book_id, status, delivery_status, deleted_flag);

CREATE INDEX idx_sys_attachment_biz_active
    ON sys_attachment (company_id, account_book_id, business_type, business_id, deleted_flag, status);
