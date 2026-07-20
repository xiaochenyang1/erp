# Inventory Finance Reconciliation Period Lock Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Current status:** 实现已落地并纳入当前发布门禁。本文件下方逐步清单保留原始执行配方和 commit 粒度，不再表示当前还有未完成开发任务。发布前以 `docs/business-readiness-checklist.md` 的预生产验收记录和 `scripts/release-check.ps1` 的最新输出为准。

**Goal:** Build monthly inventory-finance reconciliation, sales cost posting, and account-period locking so historical inventory and finance facts cannot drift after month-end.

**Architecture:** Add a focused `finance/period` module for period state, reconciliation, close checks, and write guards. Keep reconciliation real-time from `inv_txn` and `fin_voucher_entry`, and integrate `AccountPeriodGuard` only at write entry points that create or reverse inventory/finance facts. Extend finance posting so sales delivery and sales return also create inventory cost entries against `1001` and `6402`.

**Tech Stack:** Spring Boot 3.5, Java 17, MyBatis-Plus, Flyway, H2 test profile, MockMvc, JUnit 5, AssertJ.

---

## File Map

**Create:**
- `src/main/resources/db/migration/V40__finance_account_period_reconciliation_schema.sql` - account period schema, indexes, `6402` subject seed, menu/permission seed.
- `src/main/java/com/tuowei/erp/finance/period/model/AccountPeriodEntity.java` - `fin_account_period` entity.
- `src/main/java/com/tuowei/erp/finance/period/mapper/AccountPeriodMapper.java` - MyBatis mapper.
- `src/main/java/com/tuowei/erp/finance/period/controller/AccountPeriodController.java` - period, reconciliation, lock, close and reopen endpoints.
- `src/main/java/com/tuowei/erp/finance/period/service/AccountPeriodService.java` - annual generation, list, lock, close, reopen.
- `src/main/java/com/tuowei/erp/finance/period/service/AccountPeriodGuard.java` - write guard for business dates.
- `src/main/java/com/tuowei/erp/finance/period/service/AccountPeriodCloseChecker.java` - blocking month-end checks.
- `src/main/java/com/tuowei/erp/finance/period/service/InventoryFinanceReconciliationService.java` - summary and difference queries.
- `src/main/java/com/tuowei/erp/finance/period/web/AccountPeriodGenerateRequest.java`
- `src/main/java/com/tuowei/erp/finance/period/web/AccountPeriodResponse.java`
- `src/main/java/com/tuowei/erp/finance/period/web/AccountPeriodCloseCheckResponse.java`
- `src/main/java/com/tuowei/erp/finance/period/web/AccountPeriodCloseIssueResponse.java`
- `src/main/java/com/tuowei/erp/finance/period/web/InventoryFinanceReconciliationResponse.java`
- `src/main/java/com/tuowei/erp/finance/period/web/InventoryFinanceDifferenceQuery.java`
- `src/main/java/com/tuowei/erp/finance/period/web/InventoryFinanceDifferenceResponse.java`
- `src/test/java/com/tuowei/erp/finance/period/AccountPeriodControllerTest.java`
- `src/test/java/com/tuowei/erp/finance/period/InventoryFinanceReconciliationServiceTest.java`
- `src/test/java/com/tuowei/erp/finance/period/AccountPeriodGuardIntegrationTest.java`
- `src/test/java/com/tuowei/erp/finance/SalesCostPostingTest.java`

**Modify:**
- `src/main/java/com/tuowei/erp/common/security/PermissionCodes.java` - add period permissions and `HAS_` constants.
- `src/main/java/com/tuowei/erp/finance/posting/FinancePostingService.java` - add `6402`, cost posting entries, and reusable voucher entry helpers.
- `src/main/java/com/tuowei/erp/sales/delivery/service/SalesDeliveryService.java` - capture outbound cost and pass it to finance posting.
- `src/main/java/com/tuowei/erp/sales/returnorder/service/SalesReturnService.java` - pass return inventory amount to finance posting.
- `src/main/java/com/tuowei/erp/purchase/receipt/service/PurchaseReceiptService.java` - guard post date.
- `src/main/java/com/tuowei/erp/purchase/returnorder/service/PurchaseReturnService.java` - guard post date.
- `src/main/java/com/tuowei/erp/sales/delivery/service/SalesDeliveryService.java` - guard post date.
- `src/main/java/com/tuowei/erp/sales/returnorder/service/SalesReturnService.java` - guard post date.
- `src/main/java/com/tuowei/erp/inventory/adjust/service/InventoryAdjustmentService.java` - guard post date.
- `src/main/java/com/tuowei/erp/inventory/transfer/service/InventoryTransferService.java` - guard post date.
- `src/main/java/com/tuowei/erp/inventory/check/service/InventoryStockCheckService.java` - guard adjustment date if check difference posts inventory facts.
- `src/main/java/com/tuowei/erp/finance/payment/service/PaymentService.java` - guard payment create and cancel dates.
- `src/main/java/com/tuowei/erp/finance/receipt/service/ReceiptService.java` - guard receipt create and cancel dates.
- `src/main/java/com/tuowei/erp/finance/expense/service/ExpenseService.java` - guard expense post and cancel dates.
- `src/main/java/com/tuowei/erp/imports/service/ImportJobService.java` - guard initial import commit dates.
- `src/test/resources/application-test.yml` - no change expected; confirm Flyway picks up `V40`.

## Task 1: Period Schema And Permissions

**Files:**
- Create: `src/main/resources/db/migration/V40__finance_account_period_reconciliation_schema.sql`
- Modify: `src/main/java/com/tuowei/erp/common/security/PermissionCodes.java`
- Test: `src/test/java/com/tuowei/erp/db/FlywayMigrationSmokeTest.java`

- [ ] **Step 1: Write the migration**

Add `fin_account_period`, seed `6402`, finance period menu rows, role-menu grants, and sequence/menu-safe indexes:

```sql
CREATE TABLE IF NOT EXISTS fin_account_period (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    period_year INT NOT NULL,
    period_month VARCHAR(7) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'LOCKED', 'CLOSED')),
    locked_by BIGINT,
    locked_time TIMESTAMP,
    closed_by BIGINT,
    closed_time TIMESTAMP,
    reopened_by BIGINT,
    reopened_time TIMESTAMP,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_fin_account_period_book_month
    ON fin_account_period (company_id, account_book_id, period_month);
CREATE INDEX idx_fin_account_period_book_status_month
    ON fin_account_period (company_id, account_book_id, status, period_month);

INSERT INTO fin_account_subject
(id, company_id, account_book_id, subject_code, subject_name, parent_id, subject_type, balance_direction,
 status, deleted_flag, remark, created_by, updated_by, version)
VALUES
    (910008, 1, 1, '6402', '主营业务成本', NULL, 'EXPENSE', 'DEBIT', 'ACTIVE', 0, '销售出库成本结转科目', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    subject_name = VALUES(subject_name),
    subject_type = VALUES(subject_type),
    balance_direction = VALUES(balance_direction),
    status = VALUES(status),
    deleted_flag = VALUES(deleted_flag),
    remark = VALUES(remark),
    updated_by = VALUES(updated_by);

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5035, 5030, 'MENU', 'FINANCE_PERIOD', '期间锁账', '/finance/periods',
     'finance/period/index', 'finance:period:view', 5, 1, 'ACTIVE', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_type = VALUES(menu_type),
    menu_name = VALUES(menu_name),
    path = VALUES(path),
    component = VALUES(component),
    permission = VALUES(permission),
    sort_no = VALUES(sort_no),
    visible_flag = VALUES(visible_flag),
    status = VALUES(status),
    deleted_flag = VALUES(deleted_flag),
    updated_by = VALUES(updated_by);

INSERT INTO sys_role_menu
(id, role_id, menu_id, created_by)
VALUES
    (7055, 3002, 5035, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
```

- [ ] **Step 2: Add permission constants**

Add these fields to `PermissionCodes` near other finance permissions:

```java
public static final String FINANCE_PERIOD_VIEW = "finance:period:view";
public static final String FINANCE_PERIOD_MANAGE = "finance:period:manage";
public static final String FINANCE_PERIOD_CLOSE = "finance:period:close";
public static final String FINANCE_PERIOD_REOPEN = "finance:period:reopen";
```

Add matching `HAS_` constants:

```java
public static final String HAS_FINANCE_PERIOD_VIEW = "hasAuthority('" + FINANCE_PERIOD_VIEW + "')";
public static final String HAS_FINANCE_PERIOD_MANAGE = "hasAuthority('" + FINANCE_PERIOD_MANAGE + "')";
public static final String HAS_FINANCE_PERIOD_CLOSE = "hasAuthority('" + FINANCE_PERIOD_CLOSE + "')";
public static final String HAS_FINANCE_PERIOD_REOPEN = "hasAuthority('" + FINANCE_PERIOD_REOPEN + "')";
```

- [ ] **Step 3: Verify migration**

Run:

```powershell
.\mvnw.cmd -Dtest=com.tuowei.erp.db.FlywayMigrationSmokeTest test
```

Expected: `Tests run: 1, Failures: 0, Errors: 0`.

- [ ] **Step 4: Commit**

```powershell
git add src/main/resources/db/migration/V40__finance_account_period_reconciliation_schema.sql src/main/java/com/tuowei/erp/common/security/PermissionCodes.java
git commit -m "feat: add account period schema"
```

## Task 2: Period API Core

**Files:**
- Create: `src/main/java/com/tuowei/erp/finance/period/model/AccountPeriodEntity.java`
- Create: `src/main/java/com/tuowei/erp/finance/period/mapper/AccountPeriodMapper.java`
- Create: `src/main/java/com/tuowei/erp/finance/period/web/AccountPeriodGenerateRequest.java`
- Create: `src/main/java/com/tuowei/erp/finance/period/web/AccountPeriodResponse.java`
- Create: `src/main/java/com/tuowei/erp/finance/period/service/AccountPeriodService.java`
- Create: `src/main/java/com/tuowei/erp/finance/period/controller/AccountPeriodController.java`
- Test: `src/test/java/com/tuowei/erp/finance/period/AccountPeriodControllerTest.java`

- [ ] **Step 1: Write failing controller tests**

Create `AccountPeriodControllerTest` with tests for generate, list, lock/close/reopen state transitions. Seed cleanup should delete `fin_account_period` ids between `860000` and `860999` and period months from `2096-01` to `2096-12`.

Use these expectations:

```java
@Test
@WithErpUser(authorities = {"finance:period:manage", "finance:period:view"})
void generatesYearPeriodsIdempotently() throws Exception {
    mockMvc.perform(post("/api/finance/periods/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"year\":2096}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(12))
            .andExpect(jsonPath("$.data[0].periodMonth").value("2096-01"))
            .andExpect(jsonPath("$.data[0].status").value("OPEN"));

    mockMvc.perform(post("/api/finance/periods/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"year\":2096}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(12));

    Integer count = jdbcTemplate.queryForObject(
            "select count(*) from fin_account_period where company_id = 1 and account_book_id = 1 and period_year = 2096",
            Integer.class
    );
    Assertions.assertThat(count).isEqualTo(12);
}
```

- [ ] **Step 2: Run the failing tests**

```powershell
.\mvnw.cmd -Dtest=com.tuowei.erp.finance.period.AccountPeriodControllerTest test
```

Expected: compilation failure because period classes do not exist.

- [ ] **Step 3: Implement entity, mapper, DTOs, service, and controller**

Service rules:
- `generate(year)` creates 12 months for current `companyId/accountBookId`; existing months are reused.
- `list(year)` returns current company/book periods ordered by `periodMonth`.
- `lock(id)` requires `OPEN`, sets `LOCKED`, `lockedBy`, `lockedTime`.
- `close(id)` requires `LOCKED`, sets `CLOSED`, `closedBy`, `closedTime`.
- `reopen(id)` requires `LOCKED`, requires it is the latest locked period for current company/book, sets `OPEN`, `reopenedBy`, `reopenedTime`, clears no audit history.

Controller endpoints:

```java
@PostMapping("/generate")
@PreAuthorize(PermissionCodes.HAS_FINANCE_PERIOD_MANAGE)
public ApiResponse<List<AccountPeriodResponse>> generate(@Valid @RequestBody AccountPeriodGenerateRequest request)

@GetMapping
@PreAuthorize(PermissionCodes.HAS_FINANCE_PERIOD_VIEW)
public ApiResponse<List<AccountPeriodResponse>> list(@RequestParam(required = false) Integer year)

@PostMapping("/{id}/lock")
@PreAuthorize(PermissionCodes.HAS_FINANCE_PERIOD_CLOSE)
public ApiResponse<AccountPeriodResponse> lock(@PathVariable Long id)

@PostMapping("/{id}/close")
@PreAuthorize(PermissionCodes.HAS_FINANCE_PERIOD_CLOSE)
public ApiResponse<AccountPeriodResponse> close(@PathVariable Long id)

@PostMapping("/{id}/reopen")
@PreAuthorize(PermissionCodes.HAS_FINANCE_PERIOD_REOPEN)
public ApiResponse<AccountPeriodResponse> reopen(@PathVariable Long id)
```

- [ ] **Step 4: Run period API tests**

```powershell
.\mvnw.cmd -Dtest=com.tuowei.erp.finance.period.AccountPeriodControllerTest test
```

Expected: period API tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/tuowei/erp/finance/period src/test/java/com/tuowei/erp/finance/period/AccountPeriodControllerTest.java
git commit -m "feat: add account period api"
```

## Task 3: Sales Cost Posting

**Files:**
- Modify: `src/main/java/com/tuowei/erp/finance/posting/FinancePostingService.java`
- Modify: `src/main/java/com/tuowei/erp/sales/delivery/service/SalesDeliveryService.java`
- Modify: `src/main/java/com/tuowei/erp/sales/returnorder/service/SalesReturnService.java`
- Test: `src/test/java/com/tuowei/erp/finance/SalesCostPostingTest.java`

- [ ] **Step 1: Write failing tests**

Create `SalesCostPostingTest` that seeds a posted sales delivery/return through existing services or direct rows, then calls the finance posting service and asserts:

```java
Assertions.assertThat(jdbcTemplate.queryForObject(
        "select count(*) from fin_voucher_entry where company_id = 1 and subject_code = '6402' and debit_amount = 45.00",
        Integer.class
)).isEqualTo(1);

Assertions.assertThat(jdbcTemplate.queryForObject(
        "select count(*) from fin_voucher_entry where company_id = 1 and subject_code = '1001' and credit_amount = 45.00",
        Integer.class
)).isEqualTo(1);
```

Also assert sales return creates `1001` debit and `6402` credit.

- [ ] **Step 2: Run failing tests**

```powershell
.\mvnw.cmd -Dtest=com.tuowei.erp.finance.SalesCostPostingTest test
```

Expected: failure because `6402` cost entries are not generated.

- [ ] **Step 3: Extend finance posting**

Add default subject support:

```java
case "6402" -> new SubjectDefinition("6402", "主营业务成本", "EXPENSE", "DEBIT");
```

Add overloaded methods:

```java
@Transactional
public void recordSalesDelivery(SalesDeliveryEntity delivery, SalesOrderEntity order, BigDecimal costAmount, AuditMetadata audit)

@Transactional
public void recordSalesReturn(SalesReturnEntity salesReturn, SalesOrderEntity order, BigDecimal costAmount, AuditMetadata audit)
```

Implementation keeps existing revenue/receivable entries and inserts extra cost entries on the same voucher:
- sales delivery: `6402` debit, `1001` credit.
- sales return: `1001` debit, `6402` credit.

If entries already exist for voucher and subject pair, return without inserting to preserve idempotency.

- [ ] **Step 4: Pass outbound cost from sales delivery**

In `SalesDeliveryService.post`, accumulate the returned value from `inventoryPostingService.postOutbound(...)`:

```java
BigDecimal totalCostAmount = BigDecimal.ZERO;
...
BigDecimal lineCostAmount = inventoryPostingService.postOutbound(...);
totalCostAmount = ScalePrecision.amount(totalCostAmount.add(lineCostAmount));
...
financePostingService.recordSalesDelivery(delivery, order, totalCostAmount, audit);
```

- [ ] **Step 5: Pass return cost from sales return**

In `SalesReturnService.post`, accumulate `returnLine.getAmount()` for now because return inbound uses source line amount:

```java
BigDecimal totalReturnCostAmount = BigDecimal.ZERO;
...
inventoryPostingService.postInbound(...);
totalReturnCostAmount = ScalePrecision.amount(totalReturnCostAmount.add(ScalePrecision.amount(returnLine.getAmount())));
...
financePostingService.recordSalesReturn(entity, order, totalReturnCostAmount, audit);
```

- [ ] **Step 6: Run tests**

```powershell
.\mvnw.cmd -Dtest=com.tuowei.erp.finance.SalesCostPostingTest test
```

Expected: tests pass.

- [ ] **Step 7: Commit**

```powershell
git add src/main/java/com/tuowei/erp/finance/posting/FinancePostingService.java src/main/java/com/tuowei/erp/sales/delivery/service/SalesDeliveryService.java src/main/java/com/tuowei/erp/sales/returnorder/service/SalesReturnService.java src/test/java/com/tuowei/erp/finance/SalesCostPostingTest.java
git commit -m "feat: post sales inventory cost entries"
```

## Task 4: Inventory Finance Reconciliation

**Files:**
- Create: `src/main/java/com/tuowei/erp/finance/period/service/InventoryFinanceReconciliationService.java`
- Create: `src/main/java/com/tuowei/erp/finance/period/web/InventoryFinanceReconciliationResponse.java`
- Create: `src/main/java/com/tuowei/erp/finance/period/web/InventoryFinanceDifferenceQuery.java`
- Create: `src/main/java/com/tuowei/erp/finance/period/web/InventoryFinanceDifferenceResponse.java`
- Modify: `src/main/java/com/tuowei/erp/finance/period/controller/AccountPeriodController.java`
- Test: `src/test/java/com/tuowei/erp/finance/period/InventoryFinanceReconciliationServiceTest.java`

- [ ] **Step 1: Write failing reconciliation tests**

Seed:
- `fin_account_period` for `2096-05`.
- one `inv_txn` `IN` amount `100.00` with `biz_type='PURCHASE_RECEIPT'`, `biz_no='PR-1'`.
- matching `fin_voucher` and `fin_voucher_entry` `1001` debit `100.00`.
- one mismatched `inv_txn` amount `80.00`, matching `1001` debit `70.00`.

Assert summary:

```java
InventoryFinanceReconciliationResponse response = service.summary(periodId);

Assertions.assertThat(response.inventoryNetAmount()).isEqualByComparingTo("180.00");
Assertions.assertThat(response.financeInventoryNetAmount()).isEqualByComparingTo("170.00");
Assertions.assertThat(response.differenceAmount()).isEqualByComparingTo("10.00");
Assertions.assertThat(response.balanced()).isFalse();
```

Assert differences contain `AMOUNT_MISMATCH`.

- [ ] **Step 2: Run failing reconciliation tests**

```powershell
.\mvnw.cmd -Dtest=com.tuowei.erp.finance.period.InventoryFinanceReconciliationServiceTest test
```

Expected: compilation failure because service and DTOs do not exist.

- [ ] **Step 3: Implement summary and differences**

Use `JdbcTemplate` for aggregation because it is cross-table reporting logic and MyBatis-Plus wrappers are clumsy here.

Summary SQL shape:

```sql
select coalesce(sum(case when direction = 'IN' then amount else -amount end), 0)
from inv_txn
where company_id = ?
  and account_book_id = ?
  and occurred_time >= ?
  and occurred_time < ?
```

Finance SQL shape:

```sql
select coalesce(sum(debit_amount - credit_amount), 0)
from fin_voucher_entry
where company_id = ?
  and account_book_id = ?
  and subject_code = '1001'
  and biz_date >= ?
  and biz_date <= ?
```

Difference rows aggregate both sides by source key:
- inventory key: `biz_type + ':' + biz_no`
- finance key: join `fin_voucher_entry` to `fin_voucher`, key `source_type + ':' + source_no`

- [ ] **Step 4: Wire controller endpoints**

Add:

```java
@GetMapping("/{id}/reconciliation")
@PreAuthorize(PermissionCodes.HAS_FINANCE_PERIOD_VIEW)
public ApiResponse<InventoryFinanceReconciliationResponse> reconciliation(@PathVariable Long id)

@GetMapping("/{id}/reconciliation/differences")
@PreAuthorize(PermissionCodes.HAS_FINANCE_PERIOD_VIEW)
public ApiResponse<List<InventoryFinanceDifferenceResponse>> reconciliationDifferences(
        @PathVariable Long id,
        InventoryFinanceDifferenceQuery query
)
```

- [ ] **Step 5: Run tests**

```powershell
.\mvnw.cmd -Dtest=com.tuowei.erp.finance.period.InventoryFinanceReconciliationServiceTest test
```

Expected: tests pass.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/tuowei/erp/finance/period src/test/java/com/tuowei/erp/finance/period/InventoryFinanceReconciliationServiceTest.java
git commit -m "feat: add inventory finance reconciliation"
```

## Task 5: Close Checker Integration

**Files:**
- Create: `src/main/java/com/tuowei/erp/finance/period/service/AccountPeriodCloseChecker.java`
- Create: `src/main/java/com/tuowei/erp/finance/period/web/AccountPeriodCloseCheckResponse.java`
- Create: `src/main/java/com/tuowei/erp/finance/period/web/AccountPeriodCloseIssueResponse.java`
- Modify: `src/main/java/com/tuowei/erp/finance/period/service/AccountPeriodService.java`
- Modify: `src/main/java/com/tuowei/erp/finance/period/controller/AccountPeriodController.java`
- Test: `src/test/java/com/tuowei/erp/finance/period/AccountPeriodControllerTest.java`

- [ ] **Step 1: Add failing close-check tests**

Add controller tests:
- `closeCheckReportsReconciliationDifference`
- `lockRejectsWhenCloseCheckFails`
- `lockSucceedsWhenChecksPass`

Expected JSON for failure:

```java
.andExpect(jsonPath("$.data.passed").value(false))
.andExpect(jsonPath("$.data.issues[0].type").value("INVENTORY_FINANCE_RECONCILIATION"))
```

- [ ] **Step 2: Run failing tests**

```powershell
.\mvnw.cmd -Dtest=com.tuowei.erp.finance.period.AccountPeriodControllerTest test
```

Expected: failure because close-check endpoint does not return issues yet.

- [ ] **Step 3: Implement close checker**

Blocking checks for first implementation:
- Inventory-finance reconciliation difference amount must be zero.
- Every `POSTED` voucher in period must have at least one entry.
- Every `POSTED` voucher in period must have equal debit and credit total.
- `fin_payment.allocated_amount` must equal allocation sum for non-cancelled payments.
- `fin_receipt.allocated_amount` must equal allocation sum for non-cancelled receipts.
- `fin_payable` and `fin_receivable` settled amount cannot be negative or greater than original amount.
- `inv_balance.qty_on_hand` and `inv_balance.amount_on_hand` cannot be negative.

Issue type constants:
`INVENTORY_FINANCE_RECONCILIATION`, `VOUCHER_UNBALANCED`, `VOUCHER_ENTRY_MISSING`, `PAYMENT_ALLOCATION_MISMATCH`, `RECEIPT_ALLOCATION_MISMATCH`, `SETTLEMENT_AMOUNT_INVALID`, `INVENTORY_BALANCE_NEGATIVE`.

- [ ] **Step 4: Enforce checker in lock**

In `AccountPeriodService.lock`, call checker and throw `BusinessConflictException("期间月结检查未通过，不能锁定")` when `passed=false`.

- [ ] **Step 5: Run tests**

```powershell
.\mvnw.cmd -Dtest=com.tuowei.erp.finance.period.AccountPeriodControllerTest test
```

Expected: tests pass.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/tuowei/erp/finance/period src/test/java/com/tuowei/erp/finance/period/AccountPeriodControllerTest.java
git commit -m "feat: enforce period close checks"
```

## Task 6: Account Period Guard

**Files:**
- Create: `src/main/java/com/tuowei/erp/finance/period/service/AccountPeriodGuard.java`
- Modify: purchase, sales, inventory, finance, and import write services listed in File Map
- Test: `src/test/java/com/tuowei/erp/finance/period/AccountPeriodGuardIntegrationTest.java`

- [ ] **Step 1: Write failing guard tests**

Use `JdbcTemplate` to seed a `LOCKED` period for `2096-05`, then assert representative write endpoints reject with a clear message:

```java
mockMvc.perform(post("/api/finance/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(paymentJsonForDate("2096-05-18")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("2096-05 已锁定")));
```

Cover at minimum:
- payment create
- receipt create
- payment cancel
- receipt cancel
- sales delivery post
- purchase receipt post

- [ ] **Step 2: Run failing guard tests**

```powershell
.\mvnw.cmd -Dtest=com.tuowei.erp.finance.period.AccountPeriodGuardIntegrationTest test
```

Expected: endpoints still allow writes because guard is not wired.

- [ ] **Step 3: Implement guard**

`AccountPeriodGuard.requireOpen(LocalDate bizDate, String action)`:
- Resolve current company/book from `AuditMetadataFactory.current()`.
- Look up `fin_account_period` where `start_date <= bizDate <= end_date`.
- If missing, throw `BusinessConflictException("业务日期 " + bizDate + " 未生成会计期间，不能执行" + action)`.
- If status is `LOCKED` or `CLOSED`, throw `BusinessConflictException("业务日期 " + bizDate + " 所属期间 " + periodMonth + " 已" + statusText + "，不能执行" + action)`.

- [ ] **Step 4: Wire representative services first**

Inject guard and call before state changes:
- `PaymentService.create`: `paymentDate`, action `付款单创建`.
- `PaymentService.cancel`: original `paymentDate`, action `付款单作废`.
- `ReceiptService.create`: `receiptDate`, action `收款单创建`.
- `ReceiptService.cancel`: original `receiptDate`, action `收款单作废`.
- `SalesDeliveryService.post`: `deliveryDate`, action `销售出库过账`.
- `PurchaseReceiptService.post`: `receiptDate`, action `采购入库过账`.

- [ ] **Step 5: Run guard tests**

```powershell
.\mvnw.cmd -Dtest=com.tuowei.erp.finance.period.AccountPeriodGuardIntegrationTest test
```

Expected: tests pass.

- [ ] **Step 6: Wire remaining services**

Add guard calls to:
- `PurchaseReturnService.post`
- `SalesReturnService.post`
- `InventoryAdjustmentService.post`
- `InventoryTransferService.post`
- `InventoryStockCheckService.adjust`
- `ExpenseService.post`
- `ExpenseService.cancel`
- `ImportJobService.commit`

- [ ] **Step 7: Run focused related tests**

```powershell
.\mvnw.cmd -Dtest=com.tuowei.erp.finance.period.AccountPeriodGuardIntegrationTest,com.tuowei.erp.finance.FinanceSettlementCancelControllerTest test
```

Expected: tests pass.

- [ ] **Step 8: Commit**

```powershell
git add src/main/java/com/tuowei/erp/finance/period/service/AccountPeriodGuard.java src/main/java/com/tuowei/erp src/test/java/com/tuowei/erp/finance/period/AccountPeriodGuardIntegrationTest.java
git commit -m "feat: enforce account period write guard"
```

## Task 7: Final Verification And Documentation

**Files:**
- Modify: `docs/business-readiness-checklist.md`
- Modify: `docs/production-readiness-audit.md`

- [ ] **Step 1: Update business readiness docs**

Add acceptance bullets for:
- generating annual periods
- inventory-finance reconciliation before lock
- locked period rejecting historical writes
- sales delivery cost posting
- closed period being final

- [ ] **Step 2: Run full tests**

```powershell
.\mvnw.cmd test
```

Expected: `BUILD SUCCESS`, zero failures.

- [ ] **Step 3: Run release gate**

```powershell
.\scripts\release-check.ps1
```

Expected: `Release gate passed.`

- [ ] **Step 4: Commit**

```powershell
git add docs/business-readiness-checklist.md docs/production-readiness-audit.md
git commit -m "docs: add period lock readiness checks"
```

## Self-Review

- Implementation evidence: `V40__finance_account_period_reconciliation_schema.sql`、`finance/period` 模块、`SalesCostPostingTest`、`AccountPeriodControllerTest`、`InventoryFinanceReconciliationServiceTest`、`AccountPeriodGuardIntegrationTest` 已存在；`release-check` 已在 2026-05-22 跑到 `BUILD SUCCESS`。
- Remaining manual gate: 真实 MySQL/Redis、Docker Compose、月结业务样例和锁账后历史写入拦截仍需在预生产环境人工验收。
- Spec coverage: period schema, `OPEN/LOCKED/CLOSED`, reconciliation summary, difference details, sales cost posting, lock close checks, write guards, permissions, and verification are covered by Tasks 1-7.
- No placeholders: all tasks include exact file paths, commands, expected outcomes, and concrete behavior.
- Type consistency: services use `AccountPeriodService`, `AccountPeriodGuard`, `AccountPeriodCloseChecker`, and `InventoryFinanceReconciliationService`; DTO names match the file map.
