-- V165: 结算核销的本位币冲减额与已实现汇兑损益快照。
--
-- V161 给核销明细加了 base_amount（按结算汇率折算的本位币金额），但没有记录
-- 「按子账入账汇率冲减了多少本位币」，因此无法还原汇兑损益，也无法在作废时
-- 精确冲回原凭证。这里补两列：
--
--   base_settled_amount = ROUND(核销原币额 × 应收/应付入账汇率, 6)
--   fx_gain_loss_amount = base_amount - base_settled_amount
--
-- 恒等式 资金腿 = 核销冲减腿 + 预收预付腿 + 汇兑损益腿 由这两列保证，凭证
-- 与核销明细可以逐分对账。历史行保留 0 默认值：V161 起既有单据都是本位币
-- 汇率 1，base_amount 与 base_settled_amount 相等且汇兑损益为 0，不回填虚构汇率。
--
-- 纯 DDL，兼容 MySQL 与 H2 迁移校验。

ALTER TABLE fin_receipt_allocation ADD COLUMN base_settled_amount DECIMAL(20,6) NOT NULL DEFAULT 0;
ALTER TABLE fin_receipt_allocation ADD COLUMN fx_gain_loss_amount DECIMAL(20,6) NOT NULL DEFAULT 0;

ALTER TABLE fin_payment_allocation ADD COLUMN base_settled_amount DECIMAL(20,6) NOT NULL DEFAULT 0;
ALTER TABLE fin_payment_allocation ADD COLUMN fx_gain_loss_amount DECIMAL(20,6) NOT NULL DEFAULT 0;

CREATE INDEX idx_fin_receipt_allocation_company_book_receipt
    ON fin_receipt_allocation(company_id, account_book_id, receipt_id);
CREATE INDEX idx_fin_payment_allocation_company_book_payment
    ON fin_payment_allocation(company_id, account_book_id, payment_id);
