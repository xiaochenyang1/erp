# Inventory Lot Trace And Expiry Alert Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add lot trace queries, expiry alert queries, and expired-lot outbound blocking for lot-controlled inventory.

**Architecture:** Keep the feature inside the inventory stock boundary. Query APIs read existing `inv_txn` and `inv_lot_balance`; outbound enforcement stays in `InventoryPostingService`, because all physical stock exits already pass through it. No database migration is required.

**Tech Stack:** Java 17, Spring Boot 3.5.14, MyBatis-Plus 3.5.7, H2 MySQL-mode tests, Maven Wrapper on Windows.

---

## Implementation Notes

- Work in `E:\tuowei\python\erpServer\.worktrees\inventory-lot-expiry-picking`.
- Use PowerShell and Maven Wrapper commands:

```powershell
.\mvnw.cmd -q "-Dtest=InventoryLotBalanceQueryTest" test
.\mvnw.cmd -q "-Dtest=InventoryPostingLotServiceTest" test
.\mvnw.cmd test
```

- Keep responses and exception messages in Chinese where they are user-facing.
- Use existing permission `PermissionCodes.HAS_INVENTORY_STOCK_VIEW`.
- Use existing data scope methods:
  - `DataScopeService.applyInventoryTransactionScope(...)`
  - `DataScopeService.applyInventoryLotBalanceScope(...)`
- Use `ScalePrecision.quantity`, `ScalePrecision.amount`, and `ScalePrecision.unitCost` for quantities and amounts.
- Do not add a migration. Existing tables already contain `lot_no`, `production_date`, `expiry_date`, and lot balance fields.

## File Structure

### Inventory Query DTOs

- Create `src/main/java/com/tuowei/erp/inventory/stock/web/InventoryLotTraceQuery.java`
  - Page and filter fields for lot trace query.
- Create `src/main/java/com/tuowei/erp/inventory/stock/web/InventoryLotTraceResponse.java`
  - Record returned by trace endpoint, based on `inv_txn`.
- Create `src/main/java/com/tuowei/erp/inventory/stock/web/InventoryLotExpiryAlertQuery.java`
  - Page and filter fields for expiry alerts.
- Create `src/main/java/com/tuowei/erp/inventory/stock/web/InventoryLotExpiryAlertResponse.java`
  - Record returned by alert endpoint, based on `inv_lot_balance`.

### Inventory Services And Controllers

- Modify `src/main/java/com/tuowei/erp/inventory/stock/service/InventoryStockQueryService.java`
  - Add trace query method.
  - Add expiry alert query method.
  - Add expiry status and days-to-expiry calculation.
- Modify `src/main/java/com/tuowei/erp/inventory/stock/controller/InventoryStockQueryController.java`
  - Add `/api/inventory/lots/trace`.
  - Add `/api/inventory/lots/alerts`.
- Modify `src/main/java/com/tuowei/erp/inventory/stock/service/InventoryPostingService.java`
  - Reject explicit expired lot outbound.
  - Exclude expired lots from automatic lot candidates.

### Tests

- Modify `src/test/java/com/tuowei/erp/inventory/stock/InventoryLotBalanceQueryTest.java`
  - Add trace and alert query service tests.
- Modify `src/test/java/com/tuowei/erp/inventory/stock/InventoryPostingLotServiceTest.java`
  - Add expired-lot explicit and automatic outbound tests.

---

## Task 1: Add Lot Trace Query API

**Files:**
- Create: `src/main/java/com/tuowei/erp/inventory/stock/web/InventoryLotTraceQuery.java`
- Create: `src/main/java/com/tuowei/erp/inventory/stock/web/InventoryLotTraceResponse.java`
- Modify: `src/main/java/com/tuowei/erp/inventory/stock/service/InventoryStockQueryService.java`
- Modify: `src/main/java/com/tuowei/erp/inventory/stock/controller/InventoryStockQueryController.java`
- Modify: `src/test/java/com/tuowei/erp/inventory/stock/InventoryLotBalanceQueryTest.java`

- [ ] **Step 1: Add failing trace query tests**

Append two tests to `InventoryLotBalanceQueryTest`:

```java
@Test
@WithErpUser(authorities = {"inventory:stock:view"})
void tracesLotTransactionsByProductAndLot() {
    seedTraceTxn(894101L, 894201L, "TRACE-A", "TRACE_TEST", "TRACE-1", 894701L,
            "IN", "5.0000", "50.00", "2026-06-01T09:00:00");
    seedTraceTxn(894101L, 894201L, "TRACE-A", "TRACE_TEST", "TRACE-2", 894702L,
            "OUT", "2.0000", "20.00", "2026-06-02T09:00:00");
    seedTraceTxn(894101L, 894201L, "TRACE-B", "TRACE_TEST", "TRACE-3", 894703L,
            "IN", "9.0000", "90.00", "2026-06-03T09:00:00");

    InventoryLotTraceQuery query = new InventoryLotTraceQuery();
    query.setProductId(894201L);
    query.setLotNo("TRACE-A");

    PageResponse<InventoryLotTraceResponse> response = service.traceLot(query);

    Assertions.assertThat(response.records())
            .extracting(InventoryLotTraceResponse::bizNo)
            .containsExactly("TRACE-2", "TRACE-1");
    Assertions.assertThat(response.records().get(0).lotNo()).isEqualTo("TRACE-A");
    Assertions.assertThat(response.records().get(0).direction()).isEqualTo("OUT");
}

@Test
@WithErpUser(authorities = {"inventory:stock:view"})
void traceLotRespectsDirectionAndWarehouseFilters() {
    seedTraceTxn(894101L, 894201L, "TRACE-FILTER", "TRACE_TEST", "TRACE-IN", 894711L,
            "IN", "5.0000", "50.00", "2026-06-01T09:00:00");
    seedTraceTxn(894101L, 894201L, "TRACE-FILTER", "TRACE_TEST", "TRACE-OUT", 894712L,
            "OUT", "2.0000", "20.00", "2026-06-02T09:00:00");
    seedTraceTxn(894102L, 894201L, "TRACE-FILTER", "TRACE_TEST", "TRACE-WH2", 894713L,
            "OUT", "1.0000", "10.00", "2026-06-03T09:00:00");

    InventoryLotTraceQuery query = new InventoryLotTraceQuery();
    query.setProductId(894201L);
    query.setLotNo("TRACE-FILTER");
    query.setWarehouseId(894101L);
    query.setDirection("out");

    PageResponse<InventoryLotTraceResponse> response = service.traceLot(query);

    Assertions.assertThat(response.records())
            .extracting(InventoryLotTraceResponse::bizNo)
            .containsExactly("TRACE-OUT");
}
```

Add helper method to the same test class:

```java
private void seedTraceTxn(
        long warehouseId,
        long productId,
        String lotNo,
        String bizType,
        String bizNo,
        long bizLineId,
        String direction,
        String qty,
        String amount,
        String occurredTime
) {
    jdbcTemplate.update("""
            insert into inv_txn
            (id, company_id, account_book_id, warehouse_id, product_id, biz_type, biz_no, biz_line_id,
             direction, qty, amount, unit_cost, occurred_time, lot_no, production_date, expiry_date, lot_key,
             remark, created_by, updated_by, version)
            values (?, 1, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    date '2026-01-01', date '2026-12-31', ?, 'trace test', 894001, 894001, 0)
            """,
            bizLineId,
            warehouseId,
            productId,
            bizType,
            bizNo,
            bizLineId,
            direction,
            new java.math.BigDecimal(qty),
            new java.math.BigDecimal(amount),
            new java.math.BigDecimal(amount).divide(new java.math.BigDecimal(qty), 6, java.math.RoundingMode.HALF_UP),
            java.time.LocalDateTime.parse(occurredTime),
            lotNo,
            lotNo);
}
```

Update `cleanup()` in `InventoryLotBalanceQueryTest`:

```java
jdbcTemplate.update("delete from inv_txn where id between 894000 and 894999 or biz_type = 'TRACE_TEST'");
```

Add imports:

```java
import com.tuowei.erp.inventory.stock.web.InventoryLotTraceQuery;
import com.tuowei.erp.inventory.stock.web.InventoryLotTraceResponse;
```

- [ ] **Step 2: Run trace tests and verify they fail**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=InventoryLotBalanceQueryTest" test
```

Expected: FAIL at compilation because `InventoryLotTraceQuery`, `InventoryLotTraceResponse`, and `InventoryStockQueryService.traceLot(...)` do not exist.

- [ ] **Step 3: Create trace DTOs**

Create `InventoryLotTraceQuery`:

```java
package com.tuowei.erp.inventory.stock.web;

import java.time.LocalDateTime;

public class InventoryLotTraceQuery {

    private Integer pageNo;
    private Integer pageSize;
    private Long productId;
    private String lotNo;
    private Long warehouseId;
    private String direction;
    private LocalDateTime occurredTimeFrom;
    private LocalDateTime occurredTimeTo;

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getLotNo() {
        return lotNo;
    }

    public void setLotNo(String lotNo) {
        this.lotNo = lotNo;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public LocalDateTime getOccurredTimeFrom() {
        return occurredTimeFrom;
    }

    public void setOccurredTimeFrom(LocalDateTime occurredTimeFrom) {
        this.occurredTimeFrom = occurredTimeFrom;
    }

    public LocalDateTime getOccurredTimeTo() {
        return occurredTimeTo;
    }

    public void setOccurredTimeTo(LocalDateTime occurredTimeTo) {
        this.occurredTimeTo = occurredTimeTo;
    }
}
```

Create `InventoryLotTraceResponse`:

```java
package com.tuowei.erp.inventory.stock.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record InventoryLotTraceResponse(
        Long id,
        Long warehouseId,
        Long productId,
        String lotNo,
        LocalDate productionDate,
        LocalDate expiryDate,
        String bizType,
        String bizNo,
        Long bizLineId,
        String direction,
        BigDecimal qty,
        BigDecimal amount,
        BigDecimal unitCost,
        LocalDateTime occurredTime,
        String remark
) {
}
```

- [ ] **Step 4: Implement trace service method**

Add imports to `InventoryStockQueryService`:

```java
import com.tuowei.erp.inventory.stock.web.InventoryLotTraceQuery;
import com.tuowei.erp.inventory.stock.web.InventoryLotTraceResponse;
```

Add public method:

```java
@Transactional(readOnly = true)
public PageResponse<InventoryLotTraceResponse> traceLot(InventoryLotTraceQuery query) {
    InventoryLotTraceQuery safeQuery = query == null ? new InventoryLotTraceQuery() : query;
    if (safeQuery.getProductId() == null) {
        throw new IllegalArgumentException("批次追溯必须指定商品");
    }
    String lotNo = normalizeNullableText(safeQuery.getLotNo());
    if (!StringUtils.hasText(lotNo)) {
        throw new IllegalArgumentException("批次追溯必须指定批次号");
    }
    CurrentUser user = currentUser();
    Page<InventoryTransactionEntity> page = new Page<>(normalizePageNo(safeQuery.getPageNo()), normalizePageSize(safeQuery.getPageSize()));
    LambdaQueryWrapper<InventoryTransactionEntity> wrapper = buildLotTraceQuery(user.companyId(), user.accountBookId(), safeQuery, lotNo);
    wrapper = dataScopeService.applyInventoryTransactionScope(wrapper, currentSnapshot());
    Page<InventoryTransactionEntity> result = inventoryTransactionMapper.selectPage(page, wrapper);

    return new PageResponse<>(
            result.getCurrent(),
            result.getSize(),
            result.getTotal(),
            result.getRecords().stream().map(this::toLotTraceResponse).toList()
    );
}
```

Add private query builder:

```java
private LambdaQueryWrapper<InventoryTransactionEntity> buildLotTraceQuery(
        Long companyId,
        Long accountBookId,
        InventoryLotTraceQuery query,
        String lotNo
) {
    LambdaQueryWrapper<InventoryTransactionEntity> wrapper = new LambdaQueryWrapper<InventoryTransactionEntity>()
            .eq(InventoryTransactionEntity::getCompanyId, companyId)
            .eq(InventoryTransactionEntity::getAccountBookId, accountBookId)
            .eq(InventoryTransactionEntity::getProductId, query.getProductId())
            .eq(InventoryTransactionEntity::getLotNo, lotNo);
    if (query.getWarehouseId() != null) {
        wrapper.eq(InventoryTransactionEntity::getWarehouseId, query.getWarehouseId());
    }
    String direction = normalizeUpper(query.getDirection());
    if (StringUtils.hasText(direction)) {
        wrapper.eq(InventoryTransactionEntity::getDirection, direction);
    }
    if (query.getOccurredTimeFrom() != null) {
        wrapper.ge(InventoryTransactionEntity::getOccurredTime, query.getOccurredTimeFrom());
    }
    if (query.getOccurredTimeTo() != null) {
        wrapper.le(InventoryTransactionEntity::getOccurredTime, query.getOccurredTimeTo());
    }
    return wrapper
            .orderByDesc(InventoryTransactionEntity::getOccurredTime)
            .orderByDesc(InventoryTransactionEntity::getId);
}
```

Add mapper:

```java
private InventoryLotTraceResponse toLotTraceResponse(InventoryTransactionEntity entity) {
    return new InventoryLotTraceResponse(
            entity.getId(),
            entity.getWarehouseId(),
            entity.getProductId(),
            entity.getLotNo(),
            entity.getProductionDate(),
            entity.getExpiryDate(),
            entity.getBizType(),
            entity.getBizNo(),
            entity.getBizLineId(),
            entity.getDirection(),
            ScalePrecision.quantity(ScalePrecision.zeroDefault(entity.getQty())),
            ScalePrecision.amount(ScalePrecision.zeroDefault(entity.getAmount())),
            ScalePrecision.unitCost(
                    ScalePrecision.amount(ScalePrecision.zeroDefault(entity.getAmount())),
                    ScalePrecision.quantity(ScalePrecision.zeroDefault(entity.getQty()))
            ),
            entity.getOccurredTime(),
            entity.getRemark()
    );
}
```

- [ ] **Step 5: Add trace controller route**

Add imports to `InventoryStockQueryController`:

```java
import com.tuowei.erp.inventory.stock.web.InventoryLotTraceQuery;
import com.tuowei.erp.inventory.stock.web.InventoryLotTraceResponse;
```

Add route:

```java
@PreAuthorize(PermissionCodes.HAS_INVENTORY_STOCK_VIEW)
@GetMapping("/lots/trace")
public ApiResponse<PageResponse<InventoryLotTraceResponse>> traceLot(InventoryLotTraceQuery query) {
    return ApiResponse.success(inventoryStockQueryService.traceLot(query));
}
```

- [ ] **Step 6: Run trace query tests**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=InventoryLotBalanceQueryTest" test
```

Expected: PASS for the trace tests and existing lot balance tests.

- [ ] **Step 7: Commit trace query**

```powershell
git add src/main/java/com/tuowei/erp/inventory/stock/web src/main/java/com/tuowei/erp/inventory/stock/service/InventoryStockQueryService.java src/main/java/com/tuowei/erp/inventory/stock/controller/InventoryStockQueryController.java src/test/java/com/tuowei/erp/inventory/stock/InventoryLotBalanceQueryTest.java
git commit -m "feat: add lot trace query"
```

---

## Task 2: Add Expiry Alert Query API

**Files:**
- Create: `src/main/java/com/tuowei/erp/inventory/stock/web/InventoryLotExpiryAlertQuery.java`
- Create: `src/main/java/com/tuowei/erp/inventory/stock/web/InventoryLotExpiryAlertResponse.java`
- Modify: `src/main/java/com/tuowei/erp/inventory/stock/service/InventoryStockQueryService.java`
- Modify: `src/main/java/com/tuowei/erp/inventory/stock/controller/InventoryStockQueryController.java`
- Modify: `src/test/java/com/tuowei/erp/inventory/stock/InventoryLotBalanceQueryTest.java`

- [ ] **Step 1: Add failing expiry alert tests**

Append tests to `InventoryLotBalanceQueryTest`:

```java
@Test
@WithErpUser(authorities = {"inventory:stock:view"})
void listsExpiredAndExpiringAvailableLots() {
    seedAlertLot(894010L, "ALERT-EXPIRED", "2026-06-28", "5.0000", "0.0000");
    seedAlertLot(894011L, "ALERT-TODAY", "2026-06-29", "5.0000", "0.0000");
    seedAlertLot(894012L, "ALERT-SOON", "2026-07-29", "5.0000", "0.0000");
    seedAlertLot(894013L, "ALERT-LATE", "2026-07-30", "5.0000", "0.0000");

    InventoryLotExpiryAlertQuery query = new InventoryLotExpiryAlertQuery();

    PageResponse<InventoryLotExpiryAlertResponse> response = service.listLotExpiryAlerts(query);

    Assertions.assertThat(response.records())
            .extracting(InventoryLotExpiryAlertResponse::lotNo)
            .containsExactly("ALERT-EXPIRED", "ALERT-TODAY", "LOT-A", "ALERT-SOON");
    Assertions.assertThat(response.records())
            .extracting(InventoryLotExpiryAlertResponse::expiryStatus)
            .containsExactly("EXPIRED", "EXPIRING", "EXPIRING", "EXPIRING");
}

@Test
@WithErpUser(authorities = {"inventory:stock:view"})
void alertQueryCanFilterStatusAndExcludesZeroAvailableLots() {
    seedAlertLot(894010L, "ALERT-EXPIRED", "2026-06-28", "5.0000", "0.0000");
    seedAlertLot(894011L, "ALERT-ZERO", "2026-06-28", "5.0000", "5.0000");
    seedAlertLot(894012L, "ALERT-SOON", "2026-07-10", "5.0000", "0.0000");

    InventoryLotExpiryAlertQuery query = new InventoryLotExpiryAlertQuery();
    query.setStatus("expired");

    PageResponse<InventoryLotExpiryAlertResponse> response = service.listLotExpiryAlerts(query);

    Assertions.assertThat(response.records())
            .extracting(InventoryLotExpiryAlertResponse::lotNo)
            .containsExactly("ALERT-EXPIRED");
    Assertions.assertThat(response.records().get(0).daysToExpiry()).isEqualTo(-1L);
}

@Test
@WithErpUser(authorities = {"inventory:stock:view"})
void alertQueryRejectsInvalidStatus() {
    InventoryLotExpiryAlertQuery query = new InventoryLotExpiryAlertQuery();
    query.setStatus("BAD");

    Assertions.assertThatThrownBy(() -> service.listLotExpiryAlerts(query))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("预警状态不正确");
}
```

Add helper method:

```java
private void seedAlertLot(long id, String lotNo, String expiryDate, String qtyOnHand, String qtyReserved) {
    jdbcTemplate.update("""
            insert into inv_lot_balance
            (id, company_id, account_book_id, warehouse_id, product_id, lot_no,
             production_date, expiry_date, first_inbound_time, qty_on_hand, qty_reserved,
             amount_on_hand, created_by, updated_by, version)
            values (?, 1, 1, 894101, 894201, ?,
                    date '2026-01-01', ?, timestamp '2026-01-03 00:00:00',
                    ?, ?, 100.00, 894001, 894001, 0)
            """,
            id,
            lotNo,
            java.sql.Date.valueOf(expiryDate),
            new java.math.BigDecimal(qtyOnHand),
            new java.math.BigDecimal(qtyReserved));
}
```

Add imports:

```java
import com.tuowei.erp.inventory.stock.web.InventoryLotExpiryAlertQuery;
import com.tuowei.erp.inventory.stock.web.InventoryLotExpiryAlertResponse;
```

- [ ] **Step 2: Run alert tests and verify they fail**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=InventoryLotBalanceQueryTest" test
```

Expected: FAIL at compilation because `InventoryLotExpiryAlertQuery`, `InventoryLotExpiryAlertResponse`, and `InventoryStockQueryService.listLotExpiryAlerts(...)` do not exist.

- [ ] **Step 3: Create expiry alert DTOs**

Create `InventoryLotExpiryAlertQuery`:

```java
package com.tuowei.erp.inventory.stock.web;

public class InventoryLotExpiryAlertQuery {

    private Integer pageNo;
    private Integer pageSize;
    private Long warehouseId;
    private Long productId;
    private String lotNo;
    private Integer warningDays;
    private String status;

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getLotNo() {
        return lotNo;
    }

    public void setLotNo(String lotNo) {
        this.lotNo = lotNo;
    }

    public Integer getWarningDays() {
        return warningDays;
    }

    public void setWarningDays(Integer warningDays) {
        this.warningDays = warningDays;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
```

Create `InventoryLotExpiryAlertResponse`:

```java
package com.tuowei.erp.inventory.stock.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record InventoryLotExpiryAlertResponse(
        Long id,
        Long warehouseId,
        Long productId,
        String lotNo,
        LocalDate productionDate,
        LocalDate expiryDate,
        LocalDateTime firstInboundTime,
        BigDecimal qtyOnHand,
        BigDecimal qtyReserved,
        BigDecimal qtyAvailable,
        BigDecimal amountOnHand,
        String expiryStatus,
        Long daysToExpiry,
        LocalDateTime updatedTime
) {
}
```

- [ ] **Step 4: Implement expiry alert service method**

Add imports to `InventoryStockQueryService`:

```java
import com.tuowei.erp.inventory.stock.web.InventoryLotExpiryAlertQuery;
import com.tuowei.erp.inventory.stock.web.InventoryLotExpiryAlertResponse;
import java.time.temporal.ChronoUnit;
```

Add constants:

```java
private static final int DEFAULT_EXPIRY_WARNING_DAYS = 30;
private static final int MAX_EXPIRY_WARNING_DAYS = 365;
```

Add public method:

```java
@Transactional(readOnly = true)
public PageResponse<InventoryLotExpiryAlertResponse> listLotExpiryAlerts(InventoryLotExpiryAlertQuery query) {
    InventoryLotExpiryAlertQuery safeQuery = query == null ? new InventoryLotExpiryAlertQuery() : query;
    CurrentUser user = currentUser();
    LocalDate referenceDate = LocalDate.now(clock);
    int warningDays = normalizeWarningDays(safeQuery.getWarningDays());
    String status = normalizeAlertStatus(safeQuery.getStatus());
    Page<InventoryLotBalanceEntity> page = new Page<>(normalizePageNo(safeQuery.getPageNo()), normalizePageSize(safeQuery.getPageSize()));
    LambdaQueryWrapper<InventoryLotBalanceEntity> wrapper = buildLotExpiryAlertQuery(user.companyId(), user.accountBookId(), safeQuery, referenceDate, warningDays, status);
    wrapper = dataScopeService.applyInventoryLotBalanceScope(wrapper, currentSnapshot());
    Page<InventoryLotBalanceEntity> result = inventoryLotBalanceMapper.selectPage(page, wrapper);

    return new PageResponse<>(
            result.getCurrent(),
            result.getSize(),
            result.getTotal(),
            result.getRecords().stream()
                    .map(entity -> toLotExpiryAlertResponse(entity, referenceDate))
                    .toList()
    );
}
```

Add query builder:

```java
private LambdaQueryWrapper<InventoryLotBalanceEntity> buildLotExpiryAlertQuery(
        Long companyId,
        Long accountBookId,
        InventoryLotExpiryAlertQuery query,
        LocalDate referenceDate,
        int warningDays,
        String status
) {
    LocalDate warningDate = referenceDate.plusDays(warningDays);
    LambdaQueryWrapper<InventoryLotBalanceEntity> wrapper = new LambdaQueryWrapper<InventoryLotBalanceEntity>()
            .eq(InventoryLotBalanceEntity::getCompanyId, companyId)
            .eq(InventoryLotBalanceEntity::getAccountBookId, accountBookId)
            .isNotNull(InventoryLotBalanceEntity::getExpiryDate)
            .apply("qty_on_hand - qty_reserved > 0");
    if (query.getWarehouseId() != null) {
        wrapper.eq(InventoryLotBalanceEntity::getWarehouseId, query.getWarehouseId());
    }
    if (query.getProductId() != null) {
        wrapper.eq(InventoryLotBalanceEntity::getProductId, query.getProductId());
    }
    String lotNo = normalizeNullableText(query.getLotNo());
    if (StringUtils.hasText(lotNo)) {
        wrapper.apply("lot_no like {0} escape '|'", "%" + escapeLikePattern(lotNo) + "%");
    }
    if ("EXPIRED".equals(status)) {
        wrapper.lt(InventoryLotBalanceEntity::getExpiryDate, referenceDate);
    } else if ("EXPIRING".equals(status)) {
        wrapper.ge(InventoryLotBalanceEntity::getExpiryDate, referenceDate)
                .le(InventoryLotBalanceEntity::getExpiryDate, warningDate);
    } else {
        wrapper.le(InventoryLotBalanceEntity::getExpiryDate, warningDate);
    }
    return wrapper
            .orderByAsc(InventoryLotBalanceEntity::getExpiryDate)
            .orderByAsc(InventoryLotBalanceEntity::getFirstInboundTime)
            .orderByAsc(InventoryLotBalanceEntity::getId);
}
```

Add helpers:

```java
private int normalizeWarningDays(Integer warningDays) {
    if (warningDays == null) {
        return DEFAULT_EXPIRY_WARNING_DAYS;
    }
    return Math.min(Math.max(warningDays, 0), MAX_EXPIRY_WARNING_DAYS);
}

private String normalizeAlertStatus(String status) {
    String normalized = normalizeUpper(status);
    if (normalized == null) {
        return null;
    }
    if (!"EXPIRED".equals(normalized) && !"EXPIRING".equals(normalized)) {
        throw new IllegalArgumentException("预警状态不正确");
    }
    return normalized;
}

private String expiryStatus(LocalDate expiryDate, LocalDate referenceDate) {
    if (expiryDate.isBefore(referenceDate)) {
        return "EXPIRED";
    }
    return "EXPIRING";
}
```

Add mapper:

```java
private InventoryLotExpiryAlertResponse toLotExpiryAlertResponse(InventoryLotBalanceEntity entity, LocalDate referenceDate) {
    BigDecimal qtyOnHand = ScalePrecision.quantity(ScalePrecision.zeroDefault(entity.getQtyOnHand()));
    BigDecimal reserved = qtyReserved(entity);
    BigDecimal available = ScalePrecision.quantity(qtyOnHand.subtract(reserved));
    return new InventoryLotExpiryAlertResponse(
            entity.getId(),
            entity.getWarehouseId(),
            entity.getProductId(),
            entity.getLotNo(),
            entity.getProductionDate(),
            entity.getExpiryDate(),
            entity.getFirstInboundTime(),
            qtyOnHand,
            reserved,
            available,
            ScalePrecision.amount(ScalePrecision.zeroDefault(entity.getAmountOnHand())),
            expiryStatus(entity.getExpiryDate(), referenceDate),
            ChronoUnit.DAYS.between(referenceDate, entity.getExpiryDate()),
            entity.getUpdatedTime()
    );
}
```

- [ ] **Step 5: Add expiry alert controller route**

Add imports to `InventoryStockQueryController`:

```java
import com.tuowei.erp.inventory.stock.web.InventoryLotExpiryAlertQuery;
import com.tuowei.erp.inventory.stock.web.InventoryLotExpiryAlertResponse;
```

Add route:

```java
@PreAuthorize(PermissionCodes.HAS_INVENTORY_STOCK_VIEW)
@GetMapping("/lots/alerts")
public ApiResponse<PageResponse<InventoryLotExpiryAlertResponse>> lotExpiryAlerts(InventoryLotExpiryAlertQuery query) {
    return ApiResponse.success(inventoryStockQueryService.listLotExpiryAlerts(query));
}
```

- [ ] **Step 6: Run alert query tests**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=InventoryLotBalanceQueryTest" test
```

Expected: PASS.

- [ ] **Step 7: Commit expiry alert query**

```powershell
git add src/main/java/com/tuowei/erp/inventory/stock/web src/main/java/com/tuowei/erp/inventory/stock/service/InventoryStockQueryService.java src/main/java/com/tuowei/erp/inventory/stock/controller/InventoryStockQueryController.java src/test/java/com/tuowei/erp/inventory/stock/InventoryLotBalanceQueryTest.java
git commit -m "feat: add lot expiry alerts"
```

---

## Task 3: Block Expired Lot Outbound

**Files:**
- Modify: `src/main/java/com/tuowei/erp/inventory/stock/service/InventoryPostingService.java`
- Modify: `src/test/java/com/tuowei/erp/inventory/stock/InventoryPostingLotServiceTest.java`

- [ ] **Step 1: Add failing expired outbound tests**

Append tests to `InventoryPostingLotServiceTest`:

```java
@Test
void explicitOutboundRejectsExpiredLot() {
    inventoryPostingService.postInbound(inbound(LOT_SHELF_PRODUCT_ID, 8949070L, "EXPIRED-EXPLICIT", "5.0000", "50.00",
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 24)), audit());

    Assertions.assertThatThrownBy(() -> inventoryPostingService.postOutbound(
                    outbound(LOT_SHELF_PRODUCT_ID, 8949071L, "EXPIRED-EXPLICIT", "1.0000"), audit(), "批次库存不足"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("批次已过期，不能出库");

    Assertions.assertThat(lotQty(LOT_SHELF_PRODUCT_ID, "EXPIRED-EXPLICIT")).isEqualByComparingTo("5.0000");
    Assertions.assertThat(txnCount(8949071L)).isZero();
}

@Test
void autoOutboundSkipsExpiredLots() {
    inventoryPostingService.postInbound(inbound(LOT_SHELF_PRODUCT_ID, 8949072L, "EXPIRED-AUTO", "5.0000", "50.00",
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 24)), audit());
    inventoryPostingService.postInbound(inbound(LOT_SHELF_PRODUCT_ID, 8949073L, "VALID-AUTO", "5.0000", "60.00",
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 25)), laterAudit());

    BigDecimal cost = inventoryPostingService.postOutbound(
            outbound(LOT_SHELF_PRODUCT_ID, 8949074L, null, "3.0000"), audit(), "批次库存不足");

    Assertions.assertThat(cost).isEqualByComparingTo("36.00");
    Assertions.assertThat(txnLots(8949074L)).containsExactly("VALID-AUTO");
    Assertions.assertThat(lotQty(LOT_SHELF_PRODUCT_ID, "EXPIRED-AUTO")).isEqualByComparingTo("5.0000");
    Assertions.assertThat(lotQty(LOT_SHELF_PRODUCT_ID, "VALID-AUTO")).isEqualByComparingTo("2.0000");
}

@Test
void autoOutboundFailsWhenOnlyExpiredLotsHaveStock() {
    inventoryPostingService.postInbound(inbound(LOT_SHELF_PRODUCT_ID, 8949075L, "EXPIRED-ONLY", "5.0000", "50.00",
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 24)), audit());

    Assertions.assertThatThrownBy(() -> inventoryPostingService.postOutbound(
                    outbound(LOT_SHELF_PRODUCT_ID, 8949076L, null, "1.0000"), audit(), "批次库存不足"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("批次库存不足");

    Assertions.assertThat(lotQty(LOT_SHELF_PRODUCT_ID, "EXPIRED-ONLY")).isEqualByComparingTo("5.0000");
    Assertions.assertThat(txnCount(8949076L)).isZero();
}
```

- [ ] **Step 2: Run expired outbound tests and verify they fail**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=InventoryPostingLotServiceTest" test
```

Expected: FAIL because expired explicit outbound is still allowed or automatic outbound consumes expired lots.

- [ ] **Step 3: Add expiry reference date helper**

In `InventoryPostingService`, add helper:

```java
private LocalDate outboundReferenceDate(InventoryPostingCommand command, AuditMetadata audit) {
    return command.bizDate() == null ? audit.now().toLocalDate() : command.bizDate();
}
```

Add helper:

```java
private boolean expired(InventoryLotBalanceEntity lot, LocalDate referenceDate) {
    return lot.getExpiryDate() != null && lot.getExpiryDate().isBefore(referenceDate);
}
```

- [ ] **Step 4: Reject explicit expired lot outbound**

In `allocateExplicitLot(...)`, after the lot existence and availability check, add:

```java
if (expired(lot, outboundReferenceDate(command, audit))) {
    throw new IllegalArgumentException("批次已过期，不能出库");
}
```

- [ ] **Step 5: Exclude expired lots from automatic candidates**

Change `candidateLotWrapper(...)` in `InventoryPostingService`.

Add:

```java
LocalDate referenceDate = outboundReferenceDate(command, audit);
wrapper.and(query -> query
        .isNull(InventoryLotBalanceEntity::getExpiryDate)
        .or()
        .ge(InventoryLotBalanceEntity::getExpiryDate, referenceDate));
```

Place this after the positive available stock filter and before sorting.

The resulting method must still sort shelf-life controlled products by:

```java
wrapper.last("order by case when expiry_date is null then 1 else 0 end, expiry_date asc, first_inbound_time asc, id asc");
```

- [ ] **Step 6: Run expired outbound tests**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=InventoryPostingLotServiceTest" test
```

Expected: PASS.

- [ ] **Step 7: Run affected lot domain regression**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=InventoryPostingLotServiceTest,PurchaseReceiptControllerTest,SalesDeliveryControllerTest,InventoryLotDomainIntegrationTest" test
```

Expected: PASS.

- [ ] **Step 8: Commit expired outbound blocking**

```powershell
git add src/main/java/com/tuowei/erp/inventory/stock/service/InventoryPostingService.java src/test/java/com/tuowei/erp/inventory/stock/InventoryPostingLotServiceTest.java
git commit -m "feat: block expired lot outbound"
```

---

## Task 4: Final Regression And Release Gate

**Files:**
- No source files expected unless verification finds a real issue.

- [ ] **Step 1: Run inventory query and posting tests together**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=InventoryLotBalanceQueryTest,InventoryPostingLotServiceTest" test
```

Expected: PASS.

- [ ] **Step 2: Run full Maven test suite**

Run:

```powershell
.\mvnw.cmd test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Run release gate**

Run:

```powershell
.\scripts\release-check.ps1
```

Expected:

- `BUILD SUCCESS`
- `Release gate passed.`
- Verified:
  - `target\erp-server-1.0.0.jar`
  - `target\classes\META-INF\sbom\application.cdx.json`
  - `target\bom.json`

- [ ] **Step 4: Run whitespace check**

Run:

```powershell
git diff --check
```

Expected: exit code 0. CRLF warnings from Git are acceptable if no whitespace errors are reported.

- [ ] **Step 5: Inspect git status and recent commits**

Run:

```powershell
git status --short --branch
git log --oneline --decorate -6
```

Expected: clean working tree after commits.

---

## Self-Review

- Spec coverage:
  - Lot trace query: Task 1.
  - Expiry alert query: Task 2.
  - Expired explicit outbound blocking: Task 3.
  - Automatic outbound skipping expired lots: Task 3.
  - Regression and release gate: Task 4.
- Scope boundaries:
  - No database migration.
  - No joins to purchase/sales/production domain tables for trace.
  - No per-product warning-day configuration.
  - No quality status or stock freeze.
- Type consistency:
  - Query DTO names match service methods and controller imports.
  - Response fields match design spec and existing entity fields.
  - Status strings are exactly `EXPIRED` and `EXPIRING`.
