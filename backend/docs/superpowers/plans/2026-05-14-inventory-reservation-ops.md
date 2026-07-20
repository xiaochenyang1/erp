# Inventory Reservation Ops Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Current status:** 实现文件已存在：`InventoryReservationOpsController`、`InventoryReservationOpsService`、`InventoryReservationEventEntity`、`InventoryReservationEventMapper` 和相关 web DTO 均已落地。本文件保留原始 TDD 执行配方；由于当前仓库已收缩为最小回归测试集，原计划中的细粒度测试文件不再全部存在，不能把下方未勾选项当作当前开放 backlog。

**Goal:** Build production-ready inventory reservation operations: reservation events, dashboards, traceability, consistency checks, and controlled manual release.

**Architecture:** Keep `inv_reservation` as the current-state table and add append-only `inv_reservation_event` for lifecycle history. `InventoryPostingService` remains the only component that mutates reservation/balance quantities; `InventoryReservationOpsService` owns read models, consistency checks, permission/data-scope validation, and calls posting service for manual release.

**Tech Stack:** Java 17, Spring Boot 3.5, MyBatis-Plus, Flyway, H2/MySQL-compatible SQL, JUnit 5, Mockito, MockMvc.

---

### Task 1: Data Foundation

**Files:**
- Create: `src/main/resources/db/migration/V33__inventory_reservation_ops_schema.sql`
- Create: `src/main/java/com/tuowei/erp/inventory/stock/model/InventoryReservationEventEntity.java`
- Create: `src/main/java/com/tuowei/erp/inventory/stock/mapper/InventoryReservationEventMapper.java`
- Modify: `src/main/java/com/tuowei/erp/common/config/MybatisPlusConfig.java`
- Modify: `src/test/java/com/tuowei/erp/inventory/InventoryOperationSchemaMigrationTest.java`

- [ ] **Step 1: Add failing schema assertions**

Add assertions to `InventoryOperationSchemaMigrationTest.flywayCreatesInventoryOperationTables()`:

```java
assertThat(countTables("inv_reservation_event")).isEqualTo(1);
assertThat(countColumns("inv_reservation_event", "reservation_id")).isEqualTo(1);
assertThat(countColumns("inv_reservation_event", "event_type")).isEqualTo(1);
assertThat(countColumns("inv_reservation_event", "event_qty")).isEqualTo(1);
assertThat(countIndexes("idx_inv_reservation_event_company_reservation")).isEqualTo(1);
assertThat(countIndexes("idx_inv_reservation_event_company_source")).isEqualTo(1);
assertThat(countIndexes("idx_inv_reservation_event_company_balance")).isEqualTo(1);
```

- [ ] **Step 2: Run migration test and verify failure**

Run:

```powershell
.\mvnw.cmd -Dtest=com.tuowei.erp.inventory.InventoryOperationSchemaMigrationTest test
```

Expected: fail because `inv_reservation_event` does not exist.

- [ ] **Step 3: Add migration**

Create `V33__inventory_reservation_ops_schema.sql`:

```sql
CREATE TABLE IF NOT EXISTS inv_reservation_event (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    reservation_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id BIGINT NOT NULL,
    source_no VARCHAR(64) NOT NULL,
    source_line_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    event_qty DECIMAL(18, 4) NOT NULL,
    remaining_qty_before DECIMAL(18, 4) NOT NULL,
    remaining_qty_after DECIMAL(18, 4) NOT NULL,
    reason VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_inv_reservation_event_company_reservation
    ON inv_reservation_event (company_id, reservation_id, created_time);
CREATE INDEX idx_inv_reservation_event_company_source
    ON inv_reservation_event (company_id, source_type, source_id);
CREATE INDEX idx_inv_reservation_event_company_balance
    ON inv_reservation_event (company_id, warehouse_id, product_id, created_time);

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission_code, sort_no, visible,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5016, 5009, 'MENU', 'INVENTORY_RESERVATION', '库存预占', '/inventory/reservations',
     'inventory/reservation/index', 'inventory:reservation:view', 5, 1, 'ACTIVE', 0, 0, 0, 0),
    (5017, 5016, 'BUTTON', 'INVENTORY_RESERVATION_CHECK', '库存预占巡检', NULL, NULL,
     'inventory:reservation:check', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5018, 5016, 'BUTTON', 'INVENTORY_RESERVATION_RELEASE', '释放库存预占', NULL, NULL,
     'inventory:reservation:release', 2, 1, 'ACTIVE', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_type = VALUES(menu_type),
    menu_name = VALUES(menu_name),
    path = VALUES(path),
    component = VALUES(component),
    permission_code = VALUES(permission_code),
    sort_no = VALUES(sort_no),
    visible = VALUES(visible),
    status = VALUES(status),
    deleted_flag = VALUES(deleted_flag),
    updated_by = VALUES(updated_by);
```

- [ ] **Step 4: Add entity and mapper**

Create `InventoryReservationEventEntity` with fields matching the table and `@TableName("inv_reservation_event")`. Create `InventoryReservationEventMapper extends BaseMapper<InventoryReservationEventEntity>` with `@Mapper`.

- [ ] **Step 5: Register tenant table**

Add `"inv_reservation_event"` to `TENANT_TABLES` in `MybatisPlusConfig`.

- [ ] **Step 6: Run migration test**

Run:

```powershell
.\mvnw.cmd -Dtest=com.tuowei.erp.inventory.InventoryOperationSchemaMigrationTest test
```

Expected: build success.

### Task 2: Permissions And Data Scope

**Files:**
- Modify: `src/main/java/com/tuowei/erp/common/security/PermissionCodes.java`
- Modify: `src/main/java/com/tuowei/erp/common/security/DataScopeService.java`
- Modify: `src/test/java/com/tuowei/erp/testsupport/WithAdminUser.java`
- Create: `src/test/java/com/tuowei/erp/inventory/stock/InventoryReservationPermissionTest.java`
- Create: `src/test/java/com/tuowei/erp/inventory/stock/InventoryReservationDataScopeTest.java`

- [ ] **Step 1: Write failing permission test**

Create `InventoryReservationPermissionTest`:

```java
package com.tuowei.erp.inventory.stock;

import com.tuowei.erp.common.security.PermissionCodes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryReservationPermissionTest {

    @Test
    void reservationPermissionsAreExposed() {
        assertThat(PermissionCodes.allPermissions())
                .contains(
                        "inventory:reservation:view",
                        "inventory:reservation:check",
                        "inventory:reservation:release"
                );
    }
}
```

- [ ] **Step 2: Add permission constants**

Add constants and `HAS_` expressions:

```java
public static final String INVENTORY_RESERVATION_VIEW = "inventory:reservation:view";
public static final String INVENTORY_RESERVATION_CHECK = "inventory:reservation:check";
public static final String INVENTORY_RESERVATION_RELEASE = "inventory:reservation:release";

public static final String HAS_INVENTORY_RESERVATION_VIEW = "hasAuthority('" + INVENTORY_RESERVATION_VIEW + "')";
public static final String HAS_INVENTORY_RESERVATION_CHECK = "hasAuthority('" + INVENTORY_RESERVATION_CHECK + "')";
public static final String HAS_INVENTORY_RESERVATION_RELEASE = "hasAuthority('" + INVENTORY_RESERVATION_RELEASE + "')";
```

Add the three raw permission constants to `WithAdminUser.authorities`.

- [ ] **Step 3: Add reservation data-scope methods**

In `DataScopeService`, import `InventoryReservationEntity` and add:

```java
public LambdaQueryWrapper<InventoryReservationEntity> applyInventoryReservationScope(
        LambdaQueryWrapper<InventoryReservationEntity> wrapper,
        DataScopeSnapshot snapshot
) {
    return applyWarehouseScope(wrapper, snapshot, InventoryReservationEntity::getWarehouseId);
}

public void assertCanViewInventoryReservation(InventoryReservationEntity entity, DataScopeSnapshot snapshot) {
    if (canViewWarehouse(entity.getWarehouseId(), snapshot)) {
        return;
    }
    throw new AccessDeniedException("无权访问该库存预占");
}
```

- [ ] **Step 4: Run focused tests**

Run:

```powershell
.\mvnw.cmd -Dtest=com.tuowei.erp.inventory.stock.InventoryReservationPermissionTest,com.tuowei.erp.common.security.DataScopeServiceTest test
```

Expected: build success.

### Task 3: Reservation Event Lifecycle

**Files:**
- Modify: `src/main/java/com/tuowei/erp/inventory/stock/service/InventoryPostingService.java`
- Modify: `src/test/java/com/tuowei/erp/inventory/stock/InventoryPostingServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Add tests to `InventoryPostingServiceTest`:

```java
@Test
void reserveShouldWriteReserveEvent() {
    InventoryBalanceEntity balance = new InventoryBalanceEntity();
    balance.setWarehouseId(7001L);
    balance.setProductId(8001L);
    balance.setQtyOnHand(new BigDecimal("10.0000"));
    balance.setQtyReserved(new BigDecimal("0.0000"));
    balance.setAmountOnHand(new BigDecimal("100.00"));
    when(inventoryBalanceMapper.selectOne(any())).thenReturn(balance);
    when(inventoryBalanceMapper.updateById(any(InventoryBalanceEntity.class))).thenReturn(1);
    when(inventoryReservationMapper.selectOne(any())).thenReturn(null);
    when(inventoryReservationMapper.insert(any())).thenReturn(1);

    service.reserve(new InventoryReservationCommand(7001L, 8001L, "SALES_ORDER", 9001L,
            "SO202605140001", 9101L, new BigDecimal("2.0000"), "审批预占"),
            new AuditMetadata(1L, 1L, 1L, LocalDateTime.of(2026, 5, 14, 10, 0)),
            "库存可用量不足，不能审批销售订单");

    ArgumentCaptor<InventoryReservationEventEntity> eventCaptor =
            ArgumentCaptor.forClass(InventoryReservationEventEntity.class);
    verify(inventoryReservationEventMapper).insert(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getEventType()).isEqualTo("RESERVE");
    assertThat(eventCaptor.getValue().getRemainingQtyAfter()).isEqualByComparingTo("2.0000");
}

@Test
void releaseReservationShouldWriteReleaseEvent() {
    InventoryReservationEntity reservation = activeReservation(9001L, "2.0000");
    InventoryBalanceEntity balance = balance("10.0000", "2.0000");
    when(inventoryReservationMapper.selectOne(any())).thenReturn(reservation);
    when(inventoryBalanceMapper.selectOne(any())).thenReturn(balance);
    when(inventoryReservationMapper.updateById(any())).thenReturn(1);
    when(inventoryBalanceMapper.updateById(any())).thenReturn(1);

    service.releaseReservation("SALES_ORDER", 9101L, new BigDecimal("1.0000"),
            new AuditMetadata(1L, 1L, 1L, LocalDateTime.of(2026, 5, 14, 10, 30)));

    ArgumentCaptor<InventoryReservationEventEntity> eventCaptor =
            ArgumentCaptor.forClass(InventoryReservationEventEntity.class);
    verify(inventoryReservationEventMapper).insert(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getEventType()).isEqualTo("RELEASE");
    assertThat(eventCaptor.getValue().getRemainingQtyBefore()).isEqualByComparingTo("2.0000");
    assertThat(eventCaptor.getValue().getRemainingQtyAfter()).isEqualByComparingTo("1.0000");
}

@Test
void manualReleaseReservationShouldWriteManualReleaseEvent() {
    InventoryReservationEntity reservation = activeReservation(9001L, "2.0000");
    InventoryBalanceEntity balance = balance("10.0000", "2.0000");
    when(inventoryReservationMapper.selectById(9001L)).thenReturn(reservation);
    when(inventoryBalanceMapper.selectOne(any())).thenReturn(balance);
    when(inventoryReservationMapper.updateById(any())).thenReturn(1);
    when(inventoryBalanceMapper.updateById(any())).thenReturn(1);

    service.manualReleaseReservation(9001L, new BigDecimal("1.0000"),
            new AuditMetadata(1L, 1L, 1L, LocalDateTime.of(2026, 5, 14, 11, 0)),
            "客户取消，人工释放");

    ArgumentCaptor<InventoryReservationEventEntity> eventCaptor =
            ArgumentCaptor.forClass(InventoryReservationEventEntity.class);
    verify(inventoryReservationEventMapper).insert(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getEventType()).isEqualTo("MANUAL_RELEASE");
    assertThat(eventCaptor.getValue().getReason()).isEqualTo("客户取消，人工释放");
}
```

- [ ] **Step 2: Update constructor dependency**

Inject `InventoryReservationEventMapper` into `InventoryPostingService`.

- [ ] **Step 3: Implement event writing**

Add private method:

```java
private void insertReservationEvent(
        InventoryReservationEntity reservation,
        String eventType,
        BigDecimal eventQty,
        BigDecimal remainingQtyBefore,
        BigDecimal remainingQtyAfter,
        AuditMetadata audit,
        String reason,
        LocalDateTime now
)
```

It creates `InventoryReservationEventEntity` with scaled quantities and inserts through `inventoryReservationEventMapper`.

- [ ] **Step 4: Add manual release method**

Add:

```java
@Transactional
public void manualReleaseReservation(Long reservationId, BigDecimal qty, AuditMetadata audit, String reason)
```

It finds the reservation by id/company, validates `remainingQty`, updates reservation and balance with optimistic locking, writes `MANUAL_RELEASE`, and throws clear business messages.

- [ ] **Step 5: Run posting tests**

Run:

```powershell
.\mvnw.cmd -Dtest=com.tuowei.erp.inventory.stock.InventoryPostingServiceTest test
```

Expected: build success.

### Task 4: Operations Service Read Models And Checks

**Files:**
- Create: `src/main/java/com/tuowei/erp/inventory/stock/service/InventoryReservationOpsService.java`
- Create: `src/main/java/com/tuowei/erp/inventory/stock/web/InventoryReservationPageQuery.java`
- Create: `src/main/java/com/tuowei/erp/inventory/stock/web/InventoryReservationSummaryQuery.java`
- Create: `src/main/java/com/tuowei/erp/inventory/stock/web/InventoryReservationResponse.java`
- Create: `src/main/java/com/tuowei/erp/inventory/stock/web/InventoryReservationEventResponse.java`
- Create: `src/main/java/com/tuowei/erp/inventory/stock/web/InventoryReservationDetailResponse.java`
- Create: `src/main/java/com/tuowei/erp/inventory/stock/web/InventoryReservationSummaryResponse.java`
- Create: `src/main/java/com/tuowei/erp/inventory/stock/web/InventoryReservationSourceQuery.java`
- Create: `src/main/java/com/tuowei/erp/inventory/stock/web/InventoryReservationSourceResponse.java`
- Create: `src/main/java/com/tuowei/erp/inventory/stock/web/InventoryReservationCheckQuery.java`
- Create: `src/main/java/com/tuowei/erp/inventory/stock/web/InventoryReservationCheckIssueResponse.java`
- Create: `src/main/java/com/tuowei/erp/inventory/stock/web/InventoryReservationManualReleaseRequest.java`
- Create: `src/test/java/com/tuowei/erp/inventory/stock/InventoryReservationOpsServiceTest.java`

- [ ] **Step 1: Write service tests**

Cover:

```java
listReservationsShouldFilterCurrentCompanyAndWarehouseScope()
summaryShouldAggregateRemainingAndAvailableQuantities()
checksShouldReportBalanceReservedMismatch()
checksShouldReportInvalidReservationQuantities()
manualReleaseShouldRejectReservationOutsideWarehouseScope()
manualReleaseShouldWriteAuditAfterPostingServiceSucceeds()
```

- [ ] **Step 2: Implement query DTOs**

Use mutable query classes for request params and records for responses. Keep BigDecimal fields as `BigDecimal`.

- [ ] **Step 3: Implement service**

Required methods:

```java
public PageResponse<InventoryReservationResponse> listReservations(InventoryReservationPageQuery query)
public InventoryReservationDetailResponse getReservation(Long id)
public List<InventoryReservationSummaryResponse> summary(InventoryReservationSummaryQuery query)
public InventoryReservationSourceResponse source(InventoryReservationSourceQuery query)
public List<InventoryReservationCheckIssueResponse> checks(InventoryReservationCheckQuery query)
public InventoryReservationDetailResponse manualRelease(Long id, InventoryReservationManualReleaseRequest request)
```

For data scope, always call `dataScopeService.applyInventoryReservationScope(...)` or `assertCanViewInventoryReservation(...)`.

- [ ] **Step 4: Run service tests**

Run:

```powershell
.\mvnw.cmd -Dtest=com.tuowei.erp.inventory.stock.InventoryReservationOpsServiceTest test
```

Expected: build success.

### Task 5: HTTP Controller

**Files:**
- Create: `src/main/java/com/tuowei/erp/inventory/stock/controller/InventoryReservationOpsController.java`
- Create: `src/test/java/com/tuowei/erp/inventory/stock/InventoryReservationOpsControllerTest.java`

- [ ] **Step 1: Write controller tests**

Use `MockMvc` and seed `inv_balance`, `inv_reservation`, `inv_reservation_event`, `md_warehouse`, and `md_product`.

Cover:

```java
listSummaryDetailAndChecksThroughHttpApi()
manualReleaseThroughHttpApi()
manualReleaseRequiresReleasePermission()
```

- [ ] **Step 2: Implement controller**

Map:

```java
GET /api/inventory/reservations/summary
GET /api/inventory/reservations
GET /api/inventory/reservations/{id}
GET /api/inventory/reservations/source
GET /api/inventory/reservations/checks
POST /api/inventory/reservations/{id}/manual-release
```

Use `@Valid @RequestBody` for manual release.

- [ ] **Step 3: Run controller test**

Run:

```powershell
.\mvnw.cmd -Dtest=com.tuowei.erp.inventory.stock.InventoryReservationOpsControllerTest test
```

Expected: build success.

### Task 6: Integration Regression And Final Verification

**Files:**
- Modify: `src/test/java/com/tuowei/erp/sales/order/SalesOrderControllerWorkflowTest.java`
- Modify: `src/test/java/com/tuowei/erp/sales/delivery/SalesDeliveryControllerPostTest.java`

- [ ] **Step 1: Add event assertions to existing sales flow tests**

After sales order approval, assert one `RESERVE` event exists. After sales delivery post, assert one `RELEASE` event exists.

- [ ] **Step 2: Run focused sales tests**

Run:

```powershell
.\mvnw.cmd -Dtest=com.tuowei.erp.sales.order.SalesOrderControllerWorkflowTest,com.tuowei.erp.sales.delivery.SalesDeliveryControllerPostTest test
```

Expected: build success.

- [ ] **Step 3: Run full verification**

Run:

```powershell
.\mvnw.cmd test
.\mvnw.cmd -DskipTests package
git diff --check
```

Expected: all commands succeed.

- [ ] **Step 4: Commit implementation**

Stage only files changed for this feature:

```powershell
git add -- src/main src/test docs/superpowers/plans/2026-05-14-inventory-reservation-ops.md
git commit -m "feat: add inventory reservation operations"
```
