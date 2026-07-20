# Inventory Lot Expiry Picking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add lot-controlled inventory, shelf-life validation, lot balance queries, and automatic FEFO/FIFO outbound picking while preserving the existing aggregate inventory model.

**Architecture:** Keep `inv_balance` as the aggregate inventory source and add `inv_lot_balance` below `InventoryPostingService`. All physical stock changes still enter through `InventoryPostingService`; domain services only pass lot intent through `InventoryPostingCommand`. Auto-picked outbound lines keep the business document line unchanged and record one `inv_txn` row per consumed lot.

**Tech Stack:** Java 17, Spring Boot 3.5.14, MyBatis-Plus 3.5.7, Flyway, H2 MySQL mode tests, MySQL production schema.

---

## Implementation Notes

- Work on a dedicated branch or worktree before touching source code.
- Keep changes small and commit after every task.
- Use `.\mvnw.cmd ...` on Windows.
- Follow existing package style: entity/model classes under `model`, mapper interfaces under `mapper`, request/response classes under `web`, service code under `service`.
- Do not introduce lot-level reservation in this feature. `inv_lot_balance.qty_reserved` stays zero until a future reservation design.
- Use `ScalePrecision.quantity`, `ScalePrecision.amount`, and `ScalePrecision.unitCost` everywhere stock quantities or costs are calculated.
- Error messages must stay in Chinese, matching existing service style.

## File Structure

### Database

- Create `src/main/resources/db/migration/V43__inventory_lot_expiry_schema.sql`
  - Adds product control columns.
  - Adds lot metadata to inventory transactions and physical document lines.
  - Creates `inv_lot_balance`.
  - Replaces the old unique transaction idempotency index so multiple lot split rows can share the same business line.

### Inventory Core

- Create `src/main/java/com/tuowei/erp/inventory/stock/model/InventoryLotBalanceEntity.java`
- Create `src/main/java/com/tuowei/erp/inventory/stock/mapper/InventoryLotBalanceMapper.java`
- Create `src/main/java/com/tuowei/erp/inventory/stock/service/LotAllocation.java`
- Modify `src/main/java/com/tuowei/erp/inventory/stock/service/InventoryPostingCommand.java`
- Modify `src/main/java/com/tuowei/erp/inventory/stock/service/InventoryPostingService.java`
- Modify `src/main/java/com/tuowei/erp/inventory/stock/model/InventoryTransactionEntity.java`

### Product Control

- Modify `src/main/java/com/tuowei/erp/masterdata/product/model/ProductEntity.java`
- Modify `src/main/java/com/tuowei/erp/masterdata/product/web/ProductCreateRequest.java`
- Modify `src/main/java/com/tuowei/erp/masterdata/product/web/ProductUpdateRequest.java`
- Modify `src/main/java/com/tuowei/erp/masterdata/product/web/ProductResponse.java`
- Modify `src/main/java/com/tuowei/erp/masterdata/product/service/ProductService.java`

### Lot Query API

- Create `src/main/java/com/tuowei/erp/inventory/stock/web/InventoryLotBalancePageQuery.java`
- Create `src/main/java/com/tuowei/erp/inventory/stock/web/InventoryLotBalanceResponse.java`
- Modify `src/main/java/com/tuowei/erp/inventory/stock/service/InventoryStockQueryService.java`
- Modify `src/main/java/com/tuowei/erp/inventory/stock/controller/InventoryStockQueryController.java`
- Modify `src/main/java/com/tuowei/erp/common/security/DataScopeService.java`

### Domain DTOs And Entities

Modify physical stock document line models, requests, and responses:

- Purchase receipt: `purchase/receipt/model`, `purchase/receipt/web`, `PurchaseReceiptService`
- Purchase return: `purchase/returnorder/model`, `purchase/returnorder/web`, `PurchaseReturnService`
- Sales delivery: `sales/delivery/model`, `sales/delivery/web`, `SalesDeliveryService`
- Sales return: `sales/returnorder/model`, `sales/returnorder/web`, `SalesReturnService`
- Inventory adjustment/check/transfer: `inventory/adjust`, `inventory/check`, `inventory/transfer`
- Production issue/completion/return: `production/issue`, `production/completion`, `production/returnmaterial`, and request records under `production/order/web`
- Opening inventory import: `imports/service/OpeningInventoryImportHandler.java`

### Tests

- Create `src/test/java/com/tuowei/erp/inventory/stock/InventoryPostingLotServiceTest.java`
- Create `src/test/java/com/tuowei/erp/masterdata/product/ProductLotControlServiceTest.java`
- Extend `src/test/java/com/tuowei/erp/db/FlywayMigrationSmokeTest.java`
- Extend or create controller/service tests for purchase receipt, sales delivery, transfer, production issue/completion/return, and initial import.

---

## Task 1: Add Database Schema

**Files:**
- Create: `src/main/resources/db/migration/V43__inventory_lot_expiry_schema.sql`
- Modify: `src/test/java/com/tuowei/erp/db/FlywayMigrationSmokeTest.java`

- [ ] **Step 1: Add a failing migration smoke test**

Append a new test method to `FlywayMigrationSmokeTest`:

```java
@Test
void createsInventoryLotExpiryColumnsAndTables() {
    Long lotBalanceTableCount = jdbcTemplate.queryForObject("""
            select count(*)
            from information_schema.tables
            where lower(table_schema) = 'public'
              and lower(table_name) = 'inv_lot_balance'
            """, Long.class);
    Assertions.assertThat(lotBalanceTableCount).isEqualTo(1L);

    Long productColumnCount = jdbcTemplate.queryForObject("""
            select count(*)
            from information_schema.columns
            where lower(table_schema) = 'public'
              and lower(table_name) = 'md_product'
              and lower(column_name) in ('lot_controlled', 'shelf_life_controlled')
            """, Long.class);
    Assertions.assertThat(productColumnCount).isEqualTo(2L);

    Long txnColumnCount = jdbcTemplate.queryForObject("""
            select count(*)
            from information_schema.columns
            where lower(table_schema) = 'public'
              and lower(table_name) = 'inv_txn'
              and lower(column_name) in ('lot_no', 'production_date', 'expiry_date')
            """, Long.class);
    Assertions.assertThat(txnColumnCount).isEqualTo(3L);
}
```

- [ ] **Step 2: Run the failing test**

Run:

```powershell
.\mvnw.cmd -q -Dtest=FlywayMigrationSmokeTest test
```

Expected: FAIL because `inv_lot_balance` and lot columns do not exist.

- [ ] **Step 3: Create the Flyway migration**

Create `V43__inventory_lot_expiry_schema.sql` with:

```sql
ALTER TABLE md_product
    ADD COLUMN lot_controlled TINYINT NOT NULL DEFAULT 0;
ALTER TABLE md_product
    ADD COLUMN shelf_life_controlled TINYINT NOT NULL DEFAULT 0;

ALTER TABLE inv_txn
    ADD COLUMN lot_no VARCHAR(64);
ALTER TABLE inv_txn
    ADD COLUMN production_date DATE;
ALTER TABLE inv_txn
    ADD COLUMN expiry_date DATE;

CREATE TABLE IF NOT EXISTS inv_lot_balance (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    warehouse_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    lot_no VARCHAR(64) NOT NULL,
    production_date DATE,
    expiry_date DATE,
    first_inbound_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    qty_on_hand DECIMAL(18, 4) NOT NULL DEFAULT 0,
    qty_reserved DECIMAL(18, 4) NOT NULL DEFAULT 0,
    amount_on_hand DECIMAL(18, 2) NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_inv_lot_balance_company_wh_product_lot
    ON inv_lot_balance (company_id, warehouse_id, product_id, lot_no);
CREATE INDEX idx_inv_lot_balance_company_product_expiry
    ON inv_lot_balance (company_id, product_id, expiry_date);
CREATE INDEX idx_inv_lot_balance_company_wh_product
    ON inv_lot_balance (company_id, warehouse_id, product_id);
CREATE INDEX idx_inv_lot_balance_company_pick
    ON inv_lot_balance (company_id, warehouse_id, product_id, expiry_date, first_inbound_time);

DROP INDEX uk_inv_txn_company_biz_line_direction ON inv_txn;
CREATE INDEX idx_inv_txn_company_biz_line_direction
    ON inv_txn (company_id, biz_type, biz_line_id, direction);
CREATE INDEX idx_inv_txn_company_lot
    ON inv_txn (company_id, warehouse_id, product_id, lot_no);
```

Then add nullable lot columns to all physical line tables that exist in this repo:

```sql
ALTER TABLE pur_receipt_line ADD COLUMN lot_no VARCHAR(64);
ALTER TABLE pur_receipt_line ADD COLUMN production_date DATE;
ALTER TABLE pur_receipt_line ADD COLUMN expiry_date DATE;

ALTER TABLE pur_return_line ADD COLUMN lot_no VARCHAR(64);
ALTER TABLE pur_return_line ADD COLUMN production_date DATE;
ALTER TABLE pur_return_line ADD COLUMN expiry_date DATE;

ALTER TABLE sal_delivery_line ADD COLUMN lot_no VARCHAR(64);
ALTER TABLE sal_delivery_line ADD COLUMN production_date DATE;
ALTER TABLE sal_delivery_line ADD COLUMN expiry_date DATE;

ALTER TABLE sal_return_line ADD COLUMN lot_no VARCHAR(64);
ALTER TABLE sal_return_line ADD COLUMN production_date DATE;
ALTER TABLE sal_return_line ADD COLUMN expiry_date DATE;

ALTER TABLE inv_adjustment_line ADD COLUMN lot_no VARCHAR(64);
ALTER TABLE inv_adjustment_line ADD COLUMN production_date DATE;
ALTER TABLE inv_adjustment_line ADD COLUMN expiry_date DATE;

ALTER TABLE inv_stock_check_line ADD COLUMN lot_no VARCHAR(64);
ALTER TABLE inv_stock_check_line ADD COLUMN production_date DATE;
ALTER TABLE inv_stock_check_line ADD COLUMN expiry_date DATE;

ALTER TABLE inv_transfer_line ADD COLUMN lot_no VARCHAR(64);
ALTER TABLE inv_transfer_line ADD COLUMN production_date DATE;
ALTER TABLE inv_transfer_line ADD COLUMN expiry_date DATE;

ALTER TABLE prd_issue_line ADD COLUMN lot_no VARCHAR(64);
ALTER TABLE prd_issue_line ADD COLUMN production_date DATE;
ALTER TABLE prd_issue_line ADD COLUMN expiry_date DATE;

ALTER TABLE prd_completion ADD COLUMN lot_no VARCHAR(64);
ALTER TABLE prd_completion ADD COLUMN production_date DATE;
ALTER TABLE prd_completion ADD COLUMN expiry_date DATE;

ALTER TABLE prd_return_line ADD COLUMN lot_no VARCHAR(64);
ALTER TABLE prd_return_line ADD COLUMN production_date DATE;
ALTER TABLE prd_return_line ADD COLUMN expiry_date DATE;
```

- [ ] **Step 4: Run migration tests**

Run:

```powershell
.\mvnw.cmd -q -Dtest=FlywayMigrationSmokeTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add src/main/resources/db/migration/V43__inventory_lot_expiry_schema.sql src/test/java/com/tuowei/erp/db/FlywayMigrationSmokeTest.java
git commit -m "feat: add inventory lot schema"
```

---

## Task 2: Add Product Lot Control

**Files:**
- Create: `src/main/java/com/tuowei/erp/inventory/stock/model/InventoryLotBalanceEntity.java`
- Create: `src/main/java/com/tuowei/erp/inventory/stock/mapper/InventoryLotBalanceMapper.java`
- Modify: `src/main/java/com/tuowei/erp/masterdata/product/model/ProductEntity.java`
- Modify: `src/main/java/com/tuowei/erp/masterdata/product/web/ProductCreateRequest.java`
- Modify: `src/main/java/com/tuowei/erp/masterdata/product/web/ProductUpdateRequest.java`
- Modify: `src/main/java/com/tuowei/erp/masterdata/product/web/ProductResponse.java`
- Modify: `src/main/java/com/tuowei/erp/masterdata/product/service/ProductService.java`
- Create: `src/test/java/com/tuowei/erp/masterdata/product/ProductLotControlServiceTest.java`

- [ ] **Step 1: Write failing product control tests**

Create `ProductLotControlServiceTest` with tests named:

```java
@SpringBootTest
@ActiveProfiles("test")
class ProductLotControlServiceTest {

    @Autowired ProductService productService;
    @Autowired JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from inv_lot_balance where product_id between 893000 and 893999");
        jdbcTemplate.update("delete from inv_balance where product_id between 893000 and 893999");
        jdbcTemplate.update("delete from md_product where id between 893000 and 893999 or product_code like 'LOT-PROD-%'");
    }

    @Test
    @WithErpUser(authorities = {"masterdata:product:create"})
    void rejectsShelfLifeControlWithoutLotControl() {
        Assertions.assertThatThrownBy(() -> productService.create(new ProductCreateRequest(
                "LOT-PROD-001", "效期商品", "STANDARD", "批次测试", "规格", "件",
                new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("13.0000"),
                false, true, "bad flags"
        ))).isInstanceOf(IllegalArgumentException.class)
          .hasMessage("启用效期管理必须同时启用批次管理");
    }

    @Test
    @WithErpUser(authorities = {"masterdata:product:create", "masterdata:product:update"})
    void rejectsEnablingLotControlWhenAggregateStockExists() {
        ProductResponse created = productService.create(new ProductCreateRequest(
                "LOT-PROD-002", "已有库存商品", "STANDARD", "批次测试", "规格", "件",
                new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("13.0000"),
                false, false, "stock exists"
        ));
        jdbcTemplate.update("""
                insert into inv_balance
                (id, company_id, account_book_id, warehouse_id, product_id, qty_on_hand, qty_reserved,
                 amount_on_hand, created_by, updated_by, version)
                values (8931001, 1, 1, 893101, ?, 5.0000, 0.0000, 50.00, 893001, 893001, 0)
                """, created.id());

        Assertions.assertThatThrownBy(() -> productService.update(created.id(), new ProductUpdateRequest(
                "已有库存商品", "批次测试", "规格", "件",
                new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("13.0000"),
                true, false, "turn on lot"
        ))).isInstanceOf(IllegalArgumentException.class)
          .hasMessage("商品已有库存，不能直接启用批次管理");
    }
}
```

- [ ] **Step 2: Run the failing tests**

Run:

```powershell
.\mvnw.cmd -q -Dtest=ProductLotControlServiceTest test
```

Expected: FAIL because request constructors and product fields do not include lot flags.

- [ ] **Step 3: Add lot balance entity/mapper plus product fields**

Create `InventoryLotBalanceEntity` and `InventoryLotBalanceMapper` before wiring `ProductService`, because product transition validation needs to query existing lot stock. The entity must map `inv_lot_balance`, include all V43 columns, and use `@TableId(type = IdType.ASSIGN_ID)` plus `@Version`.

Create mapper:

```java
package com.tuowei.erp.inventory.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tuowei.erp.inventory.stock.model.InventoryLotBalanceEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InventoryLotBalanceMapper extends BaseMapper<InventoryLotBalanceEntity> {
}
```

Add `Boolean lotControlled` and `Boolean shelfLifeControlled` to create/update requests before `remark`. Add the same values to `ProductResponse`. Add `Integer lotControlled` and `Integer shelfLifeControlled` to `ProductEntity`.

Use integer storage in entity to match MySQL `TINYINT`; expose boolean DTOs:

```java
private boolean enabled(Integer value) {
    return value != null && value == 1;
}

private int flag(Boolean value) {
    return Boolean.TRUE.equals(value) ? 1 : 0;
}
```

- [ ] **Step 4: Add ProductService validation**

In `create`, validate flags before insert:

```java
private void validateLotFlags(boolean lotControlled, boolean shelfLifeControlled) {
    if (shelfLifeControlled && !lotControlled) {
        throw new IllegalArgumentException("启用效期管理必须同时启用批次管理");
    }
}
```

In `update`, reject unsafe transitions:

```java
if (!enabled(entity.getLotControlled()) && requestLotControlled && hasAggregateStock(entity.getId(), audit.companyId())) {
    throw new IllegalArgumentException("商品已有库存，不能直接启用批次管理");
}
if (enabled(entity.getLotControlled()) && !requestLotControlled && hasLotStock(entity.getId(), audit.companyId())) {
    throw new IllegalArgumentException("商品存在批次库存，不能关闭批次管理");
}
```

Inject `InventoryBalanceMapper` and `InventoryLotBalanceMapper` into `ProductService` for the two stock checks.

- [ ] **Step 5: Run product tests**

Run:

```powershell
.\mvnw.cmd -q -Dtest=ProductLotControlServiceTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/tuowei/erp/masterdata/product src/main/java/com/tuowei/erp/inventory/stock/model/InventoryLotBalanceEntity.java src/main/java/com/tuowei/erp/inventory/stock/mapper/InventoryLotBalanceMapper.java src/test/java/com/tuowei/erp/masterdata/product/ProductLotControlServiceTest.java
git commit -m "feat: add product lot control flags"
```

---

## Task 3: Add Lot Balance Model, Mapper, And Query API

**Files:**
- Modify: `src/main/java/com/tuowei/erp/inventory/stock/model/InventoryLotBalanceEntity.java`
- Modify: `src/main/java/com/tuowei/erp/inventory/stock/mapper/InventoryLotBalanceMapper.java`
- Create: `src/main/java/com/tuowei/erp/inventory/stock/web/InventoryLotBalancePageQuery.java`
- Create: `src/main/java/com/tuowei/erp/inventory/stock/web/InventoryLotBalanceResponse.java`
- Modify: `src/main/java/com/tuowei/erp/inventory/stock/service/InventoryStockQueryService.java`
- Modify: `src/main/java/com/tuowei/erp/inventory/stock/controller/InventoryStockQueryController.java`
- Modify: `src/main/java/com/tuowei/erp/common/security/DataScopeService.java`
- Create: `src/test/java/com/tuowei/erp/inventory/stock/InventoryLotBalanceQueryTest.java`

- [ ] **Step 1: Write failing lot balance query test**

Create `InventoryLotBalanceQueryTest` with:

```java
@SpringBootTest
@ActiveProfiles("test")
class InventoryLotBalanceQueryTest {
    @Autowired InventoryStockQueryService service;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        cleanup();
        jdbcTemplate.update("""
                insert into inv_lot_balance
                (id, company_id, account_book_id, warehouse_id, product_id, lot_no,
                 production_date, expiry_date, first_inbound_time, qty_on_hand, qty_reserved,
                 amount_on_hand, created_by, updated_by, version)
                values (894001, 1, 1, 894101, 894201, 'LOT-A',
                        date '2026-01-01', date '2026-06-30', timestamp '2026-01-02 00:00:00',
                        10.0000, 0.0000, 100.00, 894001, 894001, 0)
                """);
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from inv_lot_balance where id between 894000 and 894999");
    }

    @Test
    @WithErpUser(authorities = {"inventory:stock:view"})
    void listsLotBalancesByExpiryWindow() {
        InventoryLotBalancePageQuery query = new InventoryLotBalancePageQuery();
        query.setWarehouseId(894101L);
        query.setProductId(894201L);
        query.setExpiryDateTo(LocalDate.of(2026, 12, 31));

        PageResponse<InventoryLotBalanceResponse> response = service.listLotBalances(query);

        Assertions.assertThat(response.records()).hasSize(1);
        Assertions.assertThat(response.records().get(0).lotNo()).isEqualTo("LOT-A");
        Assertions.assertThat(response.records().get(0).qtyAvailable()).isEqualByComparingTo("10.0000");
    }
}
```

- [ ] **Step 2: Run failing query test**

Run:

```powershell
.\mvnw.cmd -q -Dtest=InventoryLotBalanceQueryTest test
```

Expected: FAIL because lot balance query classes are missing.

- [ ] **Step 3: Verify entity and mapper are available**

Task 2 should already have created `InventoryLotBalanceEntity` and `InventoryLotBalanceMapper`. Verify the entity matches the migration columns, including `accountBookId`, `firstInboundTime`, `qtyReserved`, and optimistic `version`. If any V43 columns are missing from the entity, add them in this step before implementing query APIs.

- [ ] **Step 4: Implement query DTOs**

`InventoryLotBalancePageQuery` must contain page fields and filters:

```java
private Integer pageNo = 1;
private Integer pageSize = 20;
private Long warehouseId;
private Long productId;
private String lotNo;
private LocalDate expiryDateFrom;
private LocalDate expiryDateTo;
private Integer expiringWithinDays;
```

`InventoryLotBalanceResponse` must be a record with the fields listed in the spec.

- [ ] **Step 5: Add service/controller methods**

Add `InventoryLotBalanceMapper` to `InventoryStockQueryService`. Implement `listLotBalances` and `getLotBalanceById`. Apply data scope using a new `DataScopeService.applyInventoryLotBalanceScope(...)` and `assertCanViewInventoryLotBalance(...)`.

Add controller routes:

```java
@PreAuthorize(PermissionCodes.HAS_INVENTORY_STOCK_VIEW)
@GetMapping("/lot-balances")
public ApiResponse<PageResponse<InventoryLotBalanceResponse>> listLotBalances(InventoryLotBalancePageQuery query) {
    return ApiResponse.success(inventoryStockQueryService.listLotBalances(query));
}

@PreAuthorize(PermissionCodes.HAS_INVENTORY_STOCK_VIEW)
@GetMapping("/lot-balances/{id}")
public ApiResponse<InventoryLotBalanceResponse> lotBalanceDetail(@PathVariable Long id) {
    return ApiResponse.success(inventoryStockQueryService.getLotBalanceById(id));
}
```

- [ ] **Step 6: Run query test**

Run:

```powershell
.\mvnw.cmd -q -Dtest=InventoryLotBalanceQueryTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add src/main/java/com/tuowei/erp/inventory/stock src/main/java/com/tuowei/erp/common/security/DataScopeService.java src/test/java/com/tuowei/erp/inventory/stock/InventoryLotBalanceQueryTest.java
git commit -m "feat: add inventory lot balance query"
```

---

## Task 4: Implement Lot-Aware Inventory Posting

**Files:**
- Modify: `src/main/java/com/tuowei/erp/inventory/stock/service/InventoryPostingCommand.java`
- Modify: `src/main/java/com/tuowei/erp/inventory/stock/service/InventoryPostingService.java`
- Modify: `src/main/java/com/tuowei/erp/inventory/stock/model/InventoryTransactionEntity.java`
- Create: `src/main/java/com/tuowei/erp/inventory/stock/service/LotAllocation.java`
- Create: `src/test/java/com/tuowei/erp/inventory/stock/InventoryPostingLotServiceTest.java`

- [ ] **Step 1: Write failing posting tests**

Create `InventoryPostingLotServiceTest` with test methods:

```java
void inboundCreatesLotBalanceAndTransactionMetadata()
void inboundRejectsMissingLotForLotControlledProduct()
void inboundRejectsConflictingExpiryForExistingLot()
void explicitOutboundOnlyConsumesRequestedLot()
void autoOutboundUsesFefoForShelfLifeProduct()
void autoOutboundUsesFifoForLotControlledProduct()
void autoOutboundAcrossLotsCreatesMultipleTransactions()
void autoOutboundInsufficientLotStockRollsBack()
void nonLotProductRejectsLotMetadata()
```

Seed products by inserting into `md_product` with `lot_controlled` and `shelf_life_controlled`. Seed warehouses and balances directly with `JdbcTemplate`. Use `InventoryPostingService.postInbound` and `postOutbound`.

Example assertion for FEFO:

```java
List<String> consumedLots = jdbcTemplate.queryForList("""
        select lot_no
        from inv_txn
        where biz_type = 'LOT_TEST_OUT'
          and biz_line_id = 8949001
          and direction = 'OUT'
        order by id
        """, String.class);
Assertions.assertThat(consumedLots).containsExactly("EXP-SOON", "EXP-LATER");
```

- [ ] **Step 2: Run failing posting tests**

Run:

```powershell
.\mvnw.cmd -q -Dtest=InventoryPostingLotServiceTest test
```

Expected: FAIL because posting command and service are not lot-aware.

- [ ] **Step 3: Extend command and transaction entity**

Change `InventoryPostingCommand` to:

```java
public record InventoryPostingCommand(
        Long warehouseId,
        Long productId,
        String bizType,
        String bizNo,
        Long bizLineId,
        BigDecimal qty,
        BigDecimal amount,
        String remark,
        LocalDate bizDate,
        String lotNo,
        LocalDate productionDate,
        LocalDate expiryDate
) {
    public InventoryPostingCommand(... old args ...) {
        this(..., null, null, null, null);
    }

    public InventoryPostingCommand(... old args plus bizDate ...) {
        this(..., bizDate, null, null, null);
    }
}
```

Add `lotNo`, `productionDate`, `expiryDate`, and `lotKey` to `InventoryTransactionEntity`.

- [ ] **Step 4: Add product and lot dependencies**

Inject `ProductMapper` and `InventoryLotBalanceMapper` into `InventoryPostingService`.

Add helpers:

```java
private ProductEntity requireProduct(Long companyId, Long productId)
private boolean lotControlled(ProductEntity product)
private boolean shelfLifeControlled(ProductEntity product)
private String normalizeLotNo(String lotNo)
private String lotKey(String normalizedLotNo)
private void validateLotCommandForInbound(ProductEntity product, InventoryPostingCommand command)
private void validateLotCommandForOutbound(ProductEntity product, InventoryPostingCommand command)
```

`lotKey` must return `""` for non-lot transactions and the normalized lot number for lot transactions. This must match the V43 unique index `uk_inv_txn_company_biz_line_direction_lot_key`.

- [ ] **Step 5: Implement inbound lot updates**

In `postInbound`, after idempotency check and before aggregate update, validate product flags. For lot products, insert/update `inv_lot_balance` in the same retry loop. Use immutable date checks:

```java
if (existing.getExpiryDate() != null && command.expiryDate() != null
        && !existing.getExpiryDate().equals(command.expiryDate())) {
    throw new IllegalArgumentException("批次有效期与已有批次不一致");
}
```

Set `firstInboundTime` only when inserting a new lot.

- [ ] **Step 6: Implement explicit and auto outbound allocation**

Create `LotAllocation`:

```java
public record LotAllocation(
        InventoryLotBalanceEntity lot,
        BigDecimal qty,
        BigDecimal amount
) {
}
```

In `postOutbound`, if product is lot-controlled:

- If `command.lotNo()` has text, allocate from that one lot.
- Otherwise select candidate lots sorted by FEFO/FIFO.
- Validate total available before mutating.
- Update each lot balance.
- Update aggregate `inv_balance` once using the sum of allocation amounts.
- Insert one `inv_txn` per allocation.

Update idempotency logic to use `lot_key`:

- Non-lot inbound/outbound checks use `lot_key = ''`.
- Explicit lot inbound/outbound checks use the normalized lot key.
- Auto-picked outbound retry checks may sum all existing `OUT` rows for the same business line and direction; if rows exist, return their total amount instead of inserting duplicates.
- Transaction insertion sets `lot_key = ''` for non-lot rows and the consumed lot number for lot rows.

- [ ] **Step 7: Run posting tests**

Run:

```powershell
.\mvnw.cmd -q -Dtest=InventoryPostingLotServiceTest test
```

Expected: PASS.

- [ ] **Step 8: Run existing affected tests**

Run:

```powershell
.\mvnw.cmd -q -Dtest=SalesCostPostingTest,AccountPeriodGuardIntegrationTest,InventoryFinanceReconciliationServiceTest test
```

Expected: PASS. These tests prove aggregate inventory and finance reconciliation still behave.

- [ ] **Step 9: Commit**

```powershell
git add src/main/java/com/tuowei/erp/inventory/stock src/test/java/com/tuowei/erp/inventory/stock/InventoryPostingLotServiceTest.java
git commit -m "feat: add lot aware inventory posting"
```

---

## Task 5: Wire Purchase Receipt And Sales Delivery

**Files:**
- Modify purchase receipt line entity/request/response/service under `src/main/java/com/tuowei/erp/purchase/receipt`
- Modify sales delivery line entity/request/response/service under `src/main/java/com/tuowei/erp/sales/delivery`
- Create or extend purchase/sales tests under `src/test/java/com/tuowei/erp/purchase` and `src/test/java/com/tuowei/erp/sales`

- [ ] **Step 1: Write failing purchase and sales tests**

Add tests proving:

```java
void purchaseReceiptPersistsLotFieldsAndCreatesLotStock()
void salesDeliveryWithoutLotAutoPicksFefoAndCreatesSplitTransactions()
void salesDeliveryWithExplicitLotConsumesOnlyThatLot()
```

Use existing services where possible. Seed approved purchase/sales orders the same way existing controller tests seed data.

- [ ] **Step 2: Run failing tests**

Run:

```powershell
.\mvnw.cmd -q -Dtest=PurchaseReceiptControllerTest,SalesDeliveryControllerTest test
```

Expected: FAIL because DTOs and services do not expose lot fields.

- [ ] **Step 3: Add lot fields to DTOs and line entities**

Add fields:

```java
String lotNo;
LocalDate productionDate;
LocalDate expiryDate;
```

For records, add them before `remark` so the final parameter remains comment-like.

- [ ] **Step 4: Persist line lot intent**

In `saveReceiptLines` and `saveDeliveryLines`, copy request lot fields onto entities.

For purchase receipt post, call:

```java
new InventoryPostingCommand(..., receipt.getReceiptDate(), receiptLine.getLotNo(), receiptLine.getProductionDate(), receiptLine.getExpiryDate())
```

For sales delivery post, call the same constructor with delivery date and delivery line lot fields. If lot fields are null, `InventoryPostingService` auto-picks.

- [ ] **Step 5: Return lot fields**

Update line responses so API consumers can see persisted lot intent.

- [ ] **Step 6: Run tests**

Run:

```powershell
.\mvnw.cmd -q -Dtest=PurchaseReceiptControllerTest,SalesDeliveryControllerTest,InventoryPostingLotServiceTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add src/main/java/com/tuowei/erp/purchase/receipt src/main/java/com/tuowei/erp/sales/delivery src/test/java/com/tuowei/erp/purchase src/test/java/com/tuowei/erp/sales
git commit -m "feat: wire lots into purchase receipt and sales delivery"
```

---

## Task 6: Wire Returns, Adjustment, Transfer, Production, And Opening Import

**Files:**
- Modify `src/main/java/com/tuowei/erp/purchase/returnorder`
- Modify `src/main/java/com/tuowei/erp/sales/returnorder`
- Modify `src/main/java/com/tuowei/erp/inventory/adjust`
- Modify `src/main/java/com/tuowei/erp/inventory/check`
- Modify `src/main/java/com/tuowei/erp/inventory/transfer`
- Modify `src/main/java/com/tuowei/erp/production`
- Modify `src/main/java/com/tuowei/erp/imports/service/OpeningInventoryImportHandler.java`

- [ ] **Step 1: Write failing integration tests**

Add targeted tests for:

```java
void purchaseReturnCanAutoPickLotStock()
void salesReturnRequiresLotWhenOriginalDeliverySplitAcrossLots()
void inventoryTransferAutoPickedLotsArriveInTargetWarehouse()
void productionIssueAutoPicksMaterialLots()
void productionCompletionCreatesFinishedGoodLot()
void openingInventoryImportCommitsLotStock()
```

Use direct service calls. Keep each test focused on one domain; do not build one giant end-to-end blob.

- [ ] **Step 2: Run failing tests**

Run:

```powershell
.\mvnw.cmd -q -Dtest=InitialImportControllerTest,ProductionOrderServiceTest test
```

Also run any new return/transfer test class created in Step 1.

Expected: FAIL because lot fields are not wired through these domains.

- [ ] **Step 3: Add lot fields to request/response/entity classes**

Apply the same three fields to physical line DTOs and entities:

```java
private String lotNo;
private LocalDate productionDate;
private LocalDate expiryDate;
```

For `prd_completion`, the lot fields live on the completion header because completion currently has no line table.

- [ ] **Step 4: Pass lot intent into InventoryPostingCommand**

For inbound operations:

- Sales return
- Inventory adjustment IN
- Inventory transfer inbound
- Production completion
- Production material return
- Opening inventory import

Pass lot fields to `postInbound`.

For outbound operations:

- Purchase return
- Inventory adjustment OUT
- Inventory transfer outbound
- Production issue

Pass lot fields to `postOutbound`; null lot means auto-pick.

- [ ] **Step 5: Preserve transfer split lots**

When transfer outbound auto-picks multiple lots, the target warehouse must receive the same lots. Add an inventory posting method that returns allocations for transfer use:

```java
public List<LotAllocation> postOutboundWithAllocations(InventoryPostingCommand command, AuditMetadata audit, String shortageMessage)
```

Keep existing `postOutbound` as a wrapper that returns only total amount. Use allocations to call inbound once per lot for the target warehouse.

- [ ] **Step 6: Update opening inventory import columns**

Accept `lot_no`, `production_date`, and `expiry_date` in `OpeningInventoryImportHandler.validate`. Include `lot_no` in duplicate-in-file key for lot-controlled products:

```java
warehouseCode + "|" + productCode + "|" + lotNo
```

For non-lot products, keep the old `warehouseCode + "|" + productCode` duplicate key.

- [ ] **Step 7: Run domain tests**

Run:

```powershell
.\mvnw.cmd -q -Dtest=InitialImportControllerTest,ProductionOrderServiceTest,InventoryPostingLotServiceTest test
```

Run any new return/transfer test class created in this task.

Expected: PASS.

- [ ] **Step 8: Commit**

```powershell
git add src/main/java/com/tuowei/erp/purchase/returnorder src/main/java/com/tuowei/erp/sales/returnorder src/main/java/com/tuowei/erp/inventory src/main/java/com/tuowei/erp/production src/main/java/com/tuowei/erp/imports src/test/java/com/tuowei/erp
git commit -m "feat: wire lots across inventory domains"
```

---

## Task 7: Final Regression And Release Gate

**Files:**
- Modify: `docs/business-readiness-checklist.md`
- Modify: `docs/production-readiness-audit.md`

- [ ] **Step 1: Update readiness docs**

Add a short bullet to both readiness documents that lot-controlled inventory now requires:

```markdown
- 批次/效期商品必须覆盖采购入库、销售出库自动 FEFO/FIFO、库存调拨、生产领料/完工、期初导入和批次库存查询验收。
```

- [ ] **Step 2: Run full test suite**

Run:

```powershell
.\mvnw.cmd test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Run release check**

Run:

```powershell
.\scripts\release-check.ps1
```

Expected: `Release gate passed.` and jar/SBOM artifacts verified.

- [ ] **Step 4: Inspect git status**

Run:

```powershell
git status --short --branch
```

Expected: only intended documentation changes remain before commit.

- [ ] **Step 5: Commit docs and final verification record**

```powershell
git add docs/business-readiness-checklist.md docs/production-readiness-audit.md
git commit -m "docs: add lot expiry readiness checks"
```

---

## Self-Review

- Spec coverage:
  - Product flags: Task 2.
  - Lot balance table and transaction metadata: Task 1 and Task 3.
  - Inbound/outbound explicit lot and auto FEFO/FIFO: Task 4.
  - Lot balance query API: Task 3.
  - Purchase/sales minimum business loop: Task 5.
  - Returns, transfer, production, and opening import: Task 6.
  - Regression and release gate: Task 7.
- Scope boundaries:
  - Lot-level reservation is deliberately excluded.
  - Serial number, warehouse bin/location, quality status, and split/merge operations are deliberately excluded.
- Type consistency:
  - API uses `lotNo`, `productionDate`, `expiryDate`.
  - Database uses `lot_no`, `production_date`, `expiry_date`.
  - Product API uses `lotControlled`, `shelfLifeControlled`.
  - Product database columns use `lot_controlled`, `shelf_life_controlled`.
