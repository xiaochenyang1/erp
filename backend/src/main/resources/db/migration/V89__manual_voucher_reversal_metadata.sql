-- 手工凭证作废改为红冲后，需要保留红冲凭证与作废原因。
ALTER TABLE fin_manual_voucher ADD COLUMN reversal_voucher_id BIGINT;
ALTER TABLE fin_manual_voucher ADD COLUMN cancel_reason VARCHAR(512);
