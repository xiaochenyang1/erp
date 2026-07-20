# Finance Expense Edit And Reversal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add draft expense editing and posted expense reversal so finance users can correct unfinished documents and red-flush posted expenses without mutating accounting facts.

**Architecture:** Keep the existing expense aggregate as the source of truth. Draft documents may be edited in place before posting; posted documents may be reversed by creating an opposite voucher and opposite voucher entries tied back to the same expense. The response layer should expose enough linked voucher metadata for UI and reconciliation views to show the original voucher and any reversal voucher without introducing new tables or changing the posting ledger model.

**Tech Stack:** Spring Boot 3.5.x, Spring MVC, Spring Security, MyBatis-Plus, Java 17

---

### Task 1: Extend Expense DTOs And Read Models

**Files:**
- Modify: `src/main/java/com/tuowei/erp/finance/expense/web/ExpenseResponse.java`
- Add: `src/main/java/com/tuowei/erp/finance/expense/web/ExpenseUpdateRequest.java`
- Modify: `src/main/java/com/tuowei/erp/finance/expense/web/ExpenseReconciliationResponse.java`
- Modify: `src/main/java/com/tuowei/erp/finance/voucher/service/VoucherQueryService.java`

- [x] Add draft-voucher and reversal-voucher summary fields to `ExpenseResponse`, including ids, numbers, statuses, amounts, entry counts, balance flags, and a `reversed` flag.
- [x] Add a validated expense update request with the same editable fields as create: `expenseDate`, `subjectId`, `paymentSubjectId`, `amount`, and `remark`.
- [x] Expand the reconciliation response so callers can inspect both the original voucher and any reversal voucher with their entry lists, totals, and consistency flags.
- [x] Keep `VoucherQueryService.toResponse(VoucherEntity)` public so expense reconciliation can reuse it for both the original and reversal vouchers.

### Task 2: Add Draft Edit And Posted Reversal Actions

**Files:**
- Modify: `src/main/java/com/tuowei/erp/finance/expense/controller/ExpenseController.java`
- Modify: `src/main/java/com/tuowei/erp/finance/expense/service/ExpenseService.java`

- [x] Add `PUT /api/finance/expenses/{id}` for draft-only edits under `finance:expense:manage`.
- [x] Add `POST /api/finance/expenses/{id}/reverse` for posted-only reversal under `finance:expense:manage`.
- [x] Keep draft edits in place without regenerating the document number, and reject edits once the expense is not `DRAFT`.
- [x] Create the reversal voucher with a distinct source type, mirror the original amount, and write opposite debit/credit entries so the ledger offsets the original posting.
- [x] Make reversal idempotent by returning the current expense state when the reversal voucher already exists.

### Task 3: Reconciliation And Verification

**Files:**
- Modify: `src/main/java/com/tuowei/erp/finance/expense/service/ExpenseService.java`
- No code changes for verification

- [x] Reuse the same voucher-resolution helpers to show original and reversal voucher states in the reconciliation response.
- [x] Verify the build with `.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" clean package`.
- [x] At the time of this plan, verification relied on `clean package` with no tests present; as of 2026-05-19 the repository has a minimal restored `src/test` regression set that should pass during package verification.
