-- 费用单引入审批流：DRAFT --submit--> PENDING --approve--> APPROVED --post--> POSTED，
-- 旁路 PENDING --reject--> REJECTED。需放宽 fin_expense.status 的 CHECK 约束。
-- V34 内联定义的约束在 MySQL 中自动命名为 fin_expense_chk_1（仅此一个 CHECK）。
ALTER TABLE fin_expense DROP CHECK fin_expense_chk_1;
ALTER TABLE fin_expense ADD CONSTRAINT fin_expense_chk_1
    CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'POSTED', 'CANCELLED'));
