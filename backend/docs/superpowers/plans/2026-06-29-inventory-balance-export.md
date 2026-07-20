# Inventory Balance Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `GET /api/inventory/balances/export` so the inventory module can export filtered stock balances as CSV.

**Architecture:** Keep the feature inside the existing inventory stock query boundary. The controller exposes a module-level export endpoint, while `InventoryStockQueryService` reuses the same tenant, account-book, and data-scope filters as `listBalances`.

**Tech Stack:** Java 17, Spring Boot MVC, Spring Security method authorization, MyBatis-Plus, existing `CsvExport`, JUnit 5, MockMvc.

---

### Task 1: Controller Contract Test

**Files:**
- Modify: `src/test/java/com/tuowei/erp/inventory/stock/InventoryStockQueryControllerExportTest.java`
- Modify: `src/main/java/com/tuowei/erp/inventory/stock/controller/InventoryStockQueryController.java`
- Modify: `src/main/java/com/tuowei/erp/inventory/stock/service/InventoryStockQueryService.java`

- [ ] **Step 1: Write the failing controller test**

Create `InventoryStockQueryControllerExportTest` with two behaviors:
- users without `inventory:stock:view` receive `403`;
- users with `inventory:stock:view` receive `text/csv;charset=UTF-8`, a safe attachment filename, and the request query binds to `InventoryBalancePageQuery`.

- [ ] **Step 2: Run the focused test and verify RED**

Run: `.\mvnw.cmd -B "-Dtest=InventoryStockQueryControllerExportTest" test`

Expected: the export test fails with `Status expected:<200> but was:<404>` because `/api/inventory/balances/export` does not exist yet.

- [ ] **Step 3: Add the minimal controller endpoint**

Add `@GetMapping("/balances/export")` to `InventoryStockQueryController`, authorize with `PermissionCodes.HAS_INVENTORY_STOCK_VIEW`, return a `ResponseEntity<StreamingResponseBody>`, and delegate to `inventoryStockQueryService.exportBalances(query)`.

- [ ] **Step 4: Run the focused test and verify the next missing piece**

Run: `.\mvnw.cmd -B "-Dtest=InventoryStockQueryControllerExportTest" test`

Expected: compile fails until `InventoryStockQueryService#exportBalances` exists.

### Task 2: CSV Export Service

**Files:**
- Modify: `src/main/java/com/tuowei/erp/inventory/stock/service/InventoryStockQueryService.java`
- Modify: `src/test/java/com/tuowei/erp/inventory/stock/InventoryStockQueryControllerExportTest.java`

- [ ] **Step 1: Add service export implementation**

Add `exportBalances(InventoryBalancePageQuery query)` returning `StreamingResponseBody`. Capture the current `Authentication`, stream CSV rows with `CsvExport.write`, and query using the same `buildBalanceQuery` plus `dataScopeService.applyInventoryBalanceScope` path used by `listBalances`.

- [ ] **Step 2: Limit export size conservatively**

Set a safe query copy to `pageNo = 1` and `pageSize = 5000` before selecting records. This matches existing report export guard intent without broadening scope into a new shared export policy.

- [ ] **Step 3: Run focused tests**

Run: `.\mvnw.cmd -B "-Dtest=InventoryStockQueryControllerExportTest" test`

Expected: all tests in the class pass.

### Task 3: Verification

**Files:**
- Modify: `src/main/java/com/tuowei/erp/inventory/stock/controller/InventoryStockQueryController.java`
- Modify: `src/main/java/com/tuowei/erp/inventory/stock/service/InventoryStockQueryService.java`
- Test: `src/test/java/com/tuowei/erp/inventory/stock/InventoryStockQueryControllerExportTest.java`

- [ ] **Step 1: Run nearby inventory stock tests**

Run: `.\mvnw.cmd -B "-Dtest=InventoryStockQueryControllerExportTest,InventoryStockQueryServiceTenantBoundaryTest,InventoryLotBalanceQueryTest" test`

Expected: all selected tests pass.

- [ ] **Step 2: Run the full suite**

Run: `.\mvnw.cmd -B test`

Expected: Maven output reports `Failures: 0, Errors: 0`.

- [ ] **Step 3: Scan Surefire reports because failures are ignored by Maven**

Run: `rg -n "Failures: [1-9]|Errors: [1-9]" target\surefire-reports -g "*.txt"`

Expected: no output.

---

Self-review:
- Spec coverage: the plan adds the requested `/api/inventory/balances/export`, keeps permissions aligned with stock view, and verifies CSV headers/body.
- Placeholder scan: no open implementation placeholders remain.
- Type consistency: endpoint and query names match existing `InventoryStockQueryController`, `InventoryStockQueryService`, and `InventoryBalancePageQuery`.
