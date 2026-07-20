# ERP Light Finance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐轻量财务主链路，让采购入库/退货形成应付依据，销售出库/退货形成应收依据，并提供付款、收款、核销和最小凭证追溯能力。

**Architecture:** 财务域落在 `src/main/java/com/tuowei/erp/finance` 下，按 `payable + payment + receivable + receipt + voucher + posting` 拆分。业务单据过账时只调用 `FinancePostingService` 生成财务依据和凭证，付款/收款服务负责核销并更新累计已核销金额，采购和销售服务不直接操作 `fin_*` 表细节。

**Tech Stack:** Spring Boot 3.3.x, Spring Security, MyBatis-Plus, Flyway, H2, MockMvc, JUnit 5

---

## File Map

**Create:**
- `src/main/resources/db/migration/V18__finance_receivable_payable_schema.sql`
- `src/main/java/com/tuowei/erp/finance/posting/FinancePostingService.java`
- `src/main/java/com/tuowei/erp/finance/payable/**`
- `src/main/java/com/tuowei/erp/finance/payment/**`
- `src/main/java/com/tuowei/erp/finance/receivable/**`
- `src/main/java/com/tuowei/erp/finance/receipt/**`
- `src/main/java/com/tuowei/erp/finance/voucher/**`
- `src/test/java/com/tuowei/erp/finance/**`

**Modify:**
- `src/main/java/com/tuowei/erp/common/security/PermissionCodes.java`
- `src/test/java/com/tuowei/erp/testsupport/WithAdminUser.java`
- `src/main/java/com/tuowei/erp/purchase/receipt/service/PurchaseReceiptService.java`
- `src/main/java/com/tuowei/erp/purchase/returnorder/service/PurchaseReturnService.java`
- `src/main/java/com/tuowei/erp/sales/delivery/service/SalesDeliveryService.java`
- `src/main/java/com/tuowei/erp/sales/returnorder/service/SalesReturnService.java`

## Task 1: Add Finance Schema

**Files:**
- Create: `src/main/resources/db/migration/V18__finance_receivable_payable_schema.sql`
- Test: `src/test/java/com/tuowei/erp/finance/FinanceSchemaMigrationTest.java`

- [x] Write failing migration test asserting `fin_payable`, `fin_payment`, `fin_payment_allocation`, `fin_receivable`, `fin_receipt`, `fin_receipt_allocation`, `fin_voucher` exist.
- [x] Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=FinanceSchemaMigrationTest" test`; expected failure is missing finance tables.
- [x] Add `V18` migration with source unique indexes, payment/receipt numbers, allocation indexes, audit fields and version columns.
- [x] Re-run the same test; expected `BUILD SUCCESS`.

Rules:
- Payable/receivable source records keep `source_type`, `source_id`, `source_no`, `direction`, `original_amount`, `settled_amount` and `status`.
- Purchase receipt and sales delivery use `direction = INCREASE`; purchase return and sales return use `direction = DECREASE`.
- Remaining amount is derived as `original_amount - settled_amount` for `INCREASE` rows and `0` for `DECREASE` rows.

## Task 2: Implement Posting Service

**Files:**
- Create: `src/main/java/com/tuowei/erp/finance/posting/FinancePostingService.java`
- Create: finance entities and mappers for payable, receivable and voucher
- Test: `src/test/java/com/tuowei/erp/finance/FinancePostingServiceTest.java`

- [x] Write failing test for `recordPurchaseReceipt` creating one payable and one voucher.
- [x] Run targeted test; expected failure is missing `FinancePostingService`.
- [x] Implement payable/receivable/voucher entities, mappers and `FinancePostingService`.
- [x] Add tests for purchase return, sales delivery and sales return posting.
- [x] Re-run `FinancePostingServiceTest`; expected `BUILD SUCCESS`.

Rules:
- Posting is idempotent by unique `(source_type, source_id)` on payable/receivable and voucher.
- Amounts come from posted business document totals, not request payloads.
- Voucher keeps minimal trace fields: `voucher_no`, `source_type`, `source_id`, `source_no`, `biz_date`, `amount`, `status`.

## Task 3: Wire Business Posting

**Files:**
- Modify purchase receipt/return and sales delivery/return services
- Test: `src/test/java/com/tuowei/erp/finance/FinanceBusinessPostingIntegrationTest.java`

- [x] Write failing integration test proving posted purchase receipt creates payable and voucher.
- [x] Run targeted test; expected failure is no finance posting row.
- [x] Inject `FinancePostingService` into `PurchaseReceiptService` and call it after inventory/order updates.
- [x] Add failing assertions for purchase return, sales delivery and sales return.
- [x] Wire the remaining three services.
- [x] Re-run targeted test; expected `BUILD SUCCESS`.

Rules:
- Finance posting happens in the same transaction as business posting.
- If financial row creation fails, the source document post rolls back.
- Business services only call finance service methods; they do not touch finance mappers directly.

## Task 4: Implement Payment And Receipt Allocation

**Files:**
- Create payment/receipt services, controllers, requests and responses
- Modify `PermissionCodes.java` and `WithAdminUser.java`
- Test: `src/test/java/com/tuowei/erp/finance/FinanceSettlementControllerTest.java`

- [x] Write failing payment controller test for partial payable allocation.
- [x] Run targeted test; expected failure is missing `/api/finance/payments`.
- [x] Implement payment create/detail/list with allocation validation and payable `settled_amount` update.
- [x] Write failing receipt controller test for partial receivable allocation.
- [x] Implement receipt create/detail/list with allocation validation and receivable `settled_amount` update.
- [x] Re-run targeted test; expected `BUILD SUCCESS`.

Rules:
- Allocation total must be greater than `0` and cannot exceed payment/receipt amount.
- Allocation cannot exceed remaining amount of each payable/receivable.
- Settlement status is `UNSETTLED / PARTIALLY_SETTLED / SETTLED`.

## Task 5: Regression

**Files:**
- Existing purchase, sales, inventory and security tests

- [x] Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=Finance*Test" test`.
- [x] Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=PurchaseReceiptControllerPostTest,PurchaseReturnControllerPostTest,SalesDeliveryControllerPostTest,SalesReturnControllerPostTest" test`.
- [x] Run full `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" test`.

## Self-Review

- 规格覆盖：覆盖应付、付款、应收、收款、凭证、四类业务过账联动和部分核销。
- 占位检查：没有 `TODO`、`TBD` 或“自行补齐”类占位。
- 类型一致性：财务来源统一用 `sourceType/sourceId/sourceNo`，核销统一用 `settledAmount`，响应统一派生 `remainingAmount`。

