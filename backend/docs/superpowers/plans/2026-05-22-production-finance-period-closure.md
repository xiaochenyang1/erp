# Production Finance Period Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect production issue and completion to account-period guards, inventory transaction business dates, and finance vouchers.

**Architecture:** Keep production lifecycle ownership in the production module. Reuse `AccountPeriodGuard` before production writes inventory facts, extend `InventoryPostingCommand` with an optional business date for accurate reconciliation, and add production posting methods to `FinancePostingService` so `PRODUCTION_ISSUE` and `PRODUCTION_COMPLETION` inventory rows match `1001` voucher entries by source key.

**Tech Stack:** Spring Boot 3.5.x, Java 17, MyBatis-Plus, Flyway, JUnit 5, MockMvc, H2 test profile.

---

## File Map

**Modify:**
- `src/main/java/com/tuowei/erp/production/order/controller/ProductionOrderController.java`
- `src/main/java/com/tuowei/erp/production/order/web/ProductionIssueRequest.java`
- `src/main/java/com/tuowei/erp/production/order/web/ProductionCompletionRequest.java`
- `src/main/java/com/tuowei/erp/production/issue/service/ProductionIssueService.java`
- `src/main/java/com/tuowei/erp/production/completion/service/ProductionCompletionService.java`
- `src/main/java/com/tuowei/erp/inventory/stock/service/InventoryPostingCommand.java`
- `src/main/java/com/tuowei/erp/inventory/stock/service/InventoryPostingService.java`
- `src/main/java/com/tuowei/erp/finance/posting/FinancePostingService.java`
- `src/test/java/com/tuowei/erp/production/order/ProductionOrderServiceTest.java`
- `src/test/java/com/tuowei/erp/production/order/ProductionOrderControllerTest.java`
- `src/test/java/com/tuowei/erp/db/FlywayMigrationSmokeTest.java`

**Create:**
- `src/main/resources/db/migration/V41__production_finance_subject_seed.sql`

## Task 1: RED Tests

- [x] Add service-level production finance tests that issue and complete an order in an open period, then assert `PRODUCTION_ISSUE` posts debit `5001` and credit `1001`, `PRODUCTION_COMPLETION` posts debit `1001` and credit `5001`, and inventory-finance reconciliation is balanced.
- [x] Add controller-level guard tests proving production issue and completion reject locked-period business dates with conflict responses.
- [x] Run focused tests and verify the failures are caused by missing request dates, period guards, and production finance posting.

## Task 2: Business Dates And Guards

- [x] Add `issueDate` and `completionDate` to the existing production action request records while keeping existing callers working.
- [x] Let controller action endpoints accept optional request bodies and pass them into services.
- [x] Resolve issue date from request or `plannedStartDate`; resolve completion date from request or `plannedFinishDate`.
- [x] Call `AccountPeriodGuard.requireOpen` before production issue and completion mutate stock.

## Task 3: Inventory Date Alignment

- [x] Extend `InventoryPostingCommand` with an optional `bizDate` and retain the existing constructor.
- [x] Use `bizDate.atStartOfDay()` as `inv_txn.occurred_time` when provided; otherwise keep current audit-time behavior.
- [x] Pass production issue and completion business dates into stock posting commands.

## Task 4: Production Finance Posting

- [x] Add default subject `5001` as `生产成本`.
- [x] Seed `5001` in Flyway migration `V41__production_finance_subject_seed.sql`.
- [x] Add `recordProductionIssue` to post debit `5001`, credit `1001`.
- [x] Add `recordProductionCompletion` to post debit `1001`, credit `5001`.
- [x] Call the posting methods after successful production stock posting.

## Task 5: Verification

- [x] Run focused production and period tests.
- [x] Run Flyway migration smoke test.
- [x] Run `.\scripts\release-check.ps1`.
