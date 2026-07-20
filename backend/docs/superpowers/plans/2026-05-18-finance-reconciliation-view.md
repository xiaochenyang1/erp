# Finance Reconciliation View Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a lightweight finance reconciliation view that links expense documents to vouchers and entries, and reports amount/balance consistency without changing accounting facts.

**Architecture:** Reuse the existing finance expense, voucher, and ledger services. Extend the expense detail and voucher detail responses with source/link metadata, then add one reconciliation endpoint that aggregates the expense document, linked voucher, voucher entries, debit/credit totals, and simple consistency flags. Keep the implementation read-oriented and company-scoped; do not add new tables or alter posting behavior.

**Tech Stack:** Spring Boot 3.5.x, Spring MVC, Spring Security, MyBatis-Plus, Flyway, Java 17

---

### Task 1: Extend Expense And Voucher Views

**Files:**
- Modify: `src/main/java/com/tuowei/erp/finance/expense/web/ExpenseResponse.java`
- Add: `src/main/java/com/tuowei/erp/finance/expense/web/ExpenseReconciliationResponse.java`
- Modify: `src/main/java/com/tuowei/erp/finance/voucher/web/VoucherResponse.java`
- Modify: `src/main/java/com/tuowei/erp/finance/voucher/service/VoucherQueryService.java`

- [x] Add voucher summary fields to expense detail output so expense detail can show linked voucher number, voucher status, voucher amount, entry count, and balance/amount match flags.
- [x] Add an embedded expense-source summary to voucher detail output when the voucher source type is `EXPENSE`.
- [x] Expose `VoucherQueryService.toResponse(VoucherEntity)` for reuse by expense reconciliation.
- [x] Add `ExpenseReconciliationResponse` with `expense`, `voucher`, `entries`, `debitTotal`, `creditTotal`, `voucherMissing`, `entriesMissing`, `voucherBalanced`, `amountMatched`, and `voucherLinkedToExpense`.

### Task 2: Add Reconciliation Endpoint And Service Logic

**Files:**
- Modify: `src/main/java/com/tuowei/erp/finance/expense/controller/ExpenseController.java`
- Modify: `src/main/java/com/tuowei/erp/finance/expense/service/ExpenseService.java`

- [x] Add `GET /api/finance/expenses/{id}/reconciliation` under `finance:expense:manage`.
- [x] Load the expense, resolve its linked voucher by `voucherId` or `sourceType=EXPENSE/sourceId=expense.id`, and return all voucher entries in line order.
- [x] Compute debit and credit totals from voucher entries and report whether the voucher is balanced and whether the expense amount matches the voucher amount.
- [x] Reuse existing `VoucherQueryService` response mapping for voucher headers and entries instead of duplicating DTO conversion.

### Task 3: Verification

**Files:**
- No code changes.

- [x] Run `.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" clean package`.
- [x] Confirm the build exits with `BUILD SUCCESS`.
- [x] At the time of this plan, verification relied on `clean package` with no tests present; as of 2026-05-19 the repository has a minimal restored `src/test` regression set that should pass during package verification.
