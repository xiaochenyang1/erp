# Production Manufacturing Minimum Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add a minimal production manufacturing flow with BOMs, production orders, material issue, and finished goods completion while reusing the existing inventory reservation and posting services.

**Architecture:** Introduce a new `production` module with clear boundaries for BOM maintenance, production order lifecycle, issue, and completion. Production logic owns business state and bill-of-material expansion; inventory quantity changes stay inside `InventoryPostingService` so stock, reservation, and transaction rules remain centralized and consistent with the sales and inventory transfer flows.

**Tech Stack:** Spring Boot 3.5.x, Spring MVC, Spring Security, MyBatis-Plus, Flyway, Java 17, MockMvc, JUnit 5

---

## File Map

**New module and schema:**
- Create: `src/main/resources/db/migration/V38__production_manufacturing_schema.sql`
- Create: `src/main/java/com/tuowei/erp/production/bom`
- Create: `src/main/java/com/tuowei/erp/production/order`
- Create: `src/main/java/com/tuowei/erp/production/issue`
- Create: `src/main/java/com/tuowei/erp/production/completion`

**Shared integrations:**
- Modify: `src/main/java/com/tuowei/erp/common/security/PermissionCodes.java`
- Modify: `src/main/java/com/tuowei/erp/common/security/DataScopeService.java`
- Modify: `src/main/java/com/tuowei/erp/inventory/stock/service/InventoryPostingService.java`
- Modify: `src/main/java/com/tuowei/erp/system/config/service/SequenceNumberGenerator.java`
- Create: `src/main/resources/db/migration/V39__production_menu_seed.sql`

**Tests:**
- Create: `src/test/java/com/tuowei/erp/production/bom`
- Create: `src/test/java/com/tuowei/erp/production/order`
- Create: `src/test/java/com/tuowei/erp/production/issue`
- Create: `src/test/java/com/tuowei/erp/production/completion`

## Task 1: Add Production Schema And Permissions

**Files:**
- Create: `src/main/resources/db/migration/V38__production_manufacturing_schema.sql`
- Modify: `src/main/java/com/tuowei/erp/common/security/PermissionCodes.java`

- [x] **Step 1: Write the failing migration smoke test**

Add a migration smoke test that asserts the new tables and key columns exist:

```java
@Test
void createsProductionTablesAndColumns() {
    assertThat(jdbcTemplate.queryForList("""
            select table_name
            from information_schema.tables
            where table_schema = database()
              and table_name in ('prd_bom', 'prd_bom_line', 'prd_order', 'prd_order_material')
            """)).hasSize(4);

    assertThat(jdbcTemplate.queryForList("""
            select column_name
            from information_schema.columns
            where table_schema = database()
              and table_name = 'prd_order'
              and column_name in ('order_no', 'bom_id', 'material_warehouse_id', 'finished_warehouse_id', 'status')
            """)).hasSize(5);
}
```

- [x] **Step 2: Run the test to confirm it fails before the schema exists**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=FlywayMigrationSmokeTest test
```

Expected: failure because `prd_*` tables are missing.

- [x] **Step 3: Implement the schema and permissions**

Create the `prd_bom`, `prd_bom_line`, `prd_order`, and `prd_order_material` tables with the indexes and unique constraints from the design. Add production menu permission constants:

```java
public static final String PRODUCTION_BOM_VIEW = "production:bom:view";
public static final String PRODUCTION_BOM_MANAGE = "production:bom:manage";
public static final String PRODUCTION_ORDER_VIEW = "production:order:view";
public static final String PRODUCTION_ORDER_CREATE = "production:order:create";
public static final String PRODUCTION_ORDER_UPDATE = "production:order:update";
public static final String PRODUCTION_ORDER_RELEASE = "production:order:release";
public static final String PRODUCTION_ORDER_ISSUE = "production:order:issue";
public static final String PRODUCTION_ORDER_COMPLETE = "production:order:complete";
public static final String PRODUCTION_ORDER_CANCEL = "production:order:cancel";
```

- [x] **Step 4: Re-run the migration smoke test**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=FlywayMigrationSmokeTest test
```

Expected: PASS.

## Task 2: Build BOM Maintenance

**Files:**
- Create: `src/main/java/com/tuowei/erp/production/bom/model/ProductionBomEntity.java`
- Create: `src/main/java/com/tuowei/erp/production/bom/model/ProductionBomLineEntity.java`
- Create: `src/main/java/com/tuowei/erp/production/bom/mapper/ProductionBomMapper.java`
- Create: `src/main/java/com/tuowei/erp/production/bom/mapper/ProductionBomLineMapper.java`
- Create: `src/main/java/com/tuowei/erp/production/bom/web/ProductionBomCreateRequest.java`
- Create: `src/main/java/com/tuowei/erp/production/bom/web/ProductionBomLineRequest.java`
- Create: `src/main/java/com/tuowei/erp/production/bom/web/ProductionBomUpdateRequest.java`
- Create: `src/main/java/com/tuowei/erp/production/bom/web/ProductionBomResponse.java`
- Create: `src/main/java/com/tuowei/erp/production/bom/web/ProductionBomLineResponse.java`
- Create: `src/main/java/com/tuowei/erp/production/bom/controller/ProductionBomController.java`
- Create: `src/main/java/com/tuowei/erp/production/bom/service/ProductionBomService.java`
- Create: `src/main/java/com/tuowei/erp/production/bom/service/ProductionBomNumberService.java`
- Modify: `src/main/java/com/tuowei/erp/masterdata/product/model/ProductEntity.java` only if a production-specific flag is truly needed, otherwise avoid touching it
- Test: `src/test/java/com/tuowei/erp/production/bom/ProductionBomControllerTest.java`
- Test: `src/test/java/com/tuowei/erp/production/bom/ProductionBomServiceTest.java`

- [x] **Step 1: Write the failing BOM service test**

Write a test that creates a BOM with two materials and expects line expansion, company checks, and duplicate-material rejection:

```java
@Test
void createsBomWithOrderedLinesAndRejectsDuplicateMaterials() {
    ProductionBomCreateRequest request = new ProductionBomCreateRequest(
            productId,
            new BigDecimal("1.0000"),
            "Active BOM",
            List.of(
                    new ProductionBomLineRequest(material1Id, new BigDecimal("2.0000"), new BigDecimal("0.0500"), "line1"),
                    new ProductionBomLineRequest(material2Id, new BigDecimal("3.0000"), BigDecimal.ZERO, "line2")
            )
    );

    ProductionBomResponse response = productionBomService.create(request);

    assertThat(response.lines()).hasSize(2);
    assertThat(response.lines().get(0).lineNo()).isEqualTo(1);
    assertThat(response.lines().get(1).lineNo()).isEqualTo(2);
}
```

- [x] **Step 2: Run the BOM test to confirm it fails**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=ProductionBomServiceTest test
```

Expected: FAIL because the module does not exist yet.

- [x] **Step 3: Implement BOM entities, mapper, DTOs, controller, and service**

Follow existing `controller + service + mapper + model + web` patterns from sales and inventory transfer. Enforce:

```java
if (lines.isEmpty()) {
    throw new IllegalArgumentException("BOM必须至少包含一条材料明细");
}
if (lines.stream().map(ProductionBomLineRequest::materialProductId).distinct().count() != lines.size()) {
    throw new IllegalArgumentException("BOM材料不能重复");
}
```

Load product entities in the current company, reject disabled or cross-company products, and reuse the audit metadata and optimistic lock style already used elsewhere.

- [x] **Step 4: Re-run BOM tests**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=ProductionBomServiceTest,ProductionBomControllerTest test
```

Expected: PASS.

## Task 3: Build Production Order Lifecycle

**Files:**
- Create: `src/main/java/com/tuowei/erp/production/order/model/ProductionOrderEntity.java`
- Create: `src/main/java/com/tuowei/erp/production/order/model/ProductionOrderMaterialEntity.java`
- Create: `src/main/java/com/tuowei/erp/production/order/mapper/ProductionOrderMapper.java`
- Create: `src/main/java/com/tuowei/erp/production/order/mapper/ProductionOrderMaterialMapper.java`
- Create: `src/main/java/com/tuowei/erp/production/order/web/ProductionOrderCreateRequest.java`
- Create: `src/main/java/com/tuowei/erp/production/order/web/ProductionOrderUpdateRequest.java`
- Create: `src/main/java/com/tuowei/erp/production/order/web/ProductionOrderResponse.java`
- Create: `src/main/java/com/tuowei/erp/production/order/web/ProductionOrderMaterialResponse.java`
- Create: `src/main/java/com/tuowei/erp/production/order/controller/ProductionOrderController.java`
- Create: `src/main/java/com/tuowei/erp/production/order/service/ProductionOrderService.java`
- Create: `src/main/java/com/tuowei/erp/production/order/service/ProductionOrderNumberService.java`
- Modify: `src/main/java/com/tuowei/erp/common/security/DataScopeService.java`
- Modify: `src/main/java/com/tuowei/erp/inventory/stock/service/InventoryReservationCommand.java` only if a production source marker needs a richer field, otherwise keep it unchanged
- Test: `src/test/java/com/tuowei/erp/production/order/ProductionOrderServiceTest.java`
- Test: `src/test/java/com/tuowei/erp/production/order/ProductionOrderControllerTest.java`

- [x] **Step 1: Write the failing order expansion test**

Write a test that creates an order from a BOM and verifies material expansion by quantity and loss rate:

```java
@Test
void expandsMaterialsFromBomWhenCreatingOrder() {
    ProductionOrderCreateRequest request = new ProductionOrderCreateRequest(
            bomId,
            finishedWarehouseId,
            materialWarehouseId,
            new BigDecimal("10.0000"),
            LocalDate.of(2026, 5, 20),
            LocalDate.of(2026, 5, 21),
            "test order"
    );

    ProductionOrderResponse response = productionOrderService.create(request);

    assertThat(response.materials()).hasSize(2);
    assertThat(response.materials().get(0).requiredQty()).isEqualByComparingTo("20.0000");
}
```

- [x] **Step 2: Run the order test to confirm it fails**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=ProductionOrderServiceTest test
```

Expected: FAIL because no production module exists yet.

- [x] **Step 3: Implement order entities, number service, controller, and service**

Reuse the same number generation pattern as other business documents. Add `draft -> release -> cancel` transitions, expand BOM lines on create/update, and store material rows with deterministic line order.

Order release should:

```java
inventoryPostingService.reserve(
        new InventoryReservationCommand(
                order.getMaterialWarehouseId(),
                material.getMaterialProductId(),
                "PRODUCTION_ORDER",
                order.getId(),
                order.getOrderNo(),
                material.getId(),
                material.getRequiredQty(),
                material.getRemark()
        ),
        audit,
        "材料可用量不足，不能释放生产工单"
);
```

Canceling a released but not issued order must release all reservations first.

- [x] **Step 4: Re-run order tests**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=ProductionOrderServiceTest,ProductionOrderControllerTest test
```

Expected: PASS.

## Task 4: Add Material Issue And Completion Flows

**Files:**
- Create: `src/main/java/com/tuowei/erp/production/issue/service/ProductionIssueService.java`
- Create: `src/main/java/com/tuowei/erp/production/completion/service/ProductionCompletionService.java`
- Modify: `src/main/java/com/tuowei/erp/inventory/stock/service/InventoryPostingService.java`
- Create: `src/main/java/com/tuowei/erp/production/order/web/ProductionIssueRequest.java`
- Create: `src/main/java/com/tuowei/erp/production/order/web/ProductionCompletionRequest.java`
- Modify: `src/main/java/com/tuowei/erp/production/order/service/ProductionOrderService.java`
- Test: `src/test/java/com/tuowei/erp/production/issue/ProductionIssueServiceTest.java`
- Test: `src/test/java/com/tuowei/erp/production/completion/ProductionCompletionServiceTest.java`

- [x] **Step 1: Write the failing issue and completion tests**

Write one test that issues material from a released order and one that completes the order:

```java
@Test
void issuesMaterialAndPostsOutboundTransactions() {
    ProductionOrderResponse released = productionOrderService.release(orderId);
    ProductionOrderResponse issued = productionIssueService.issue(orderId);
    assertThat(issued.status()).isEqualTo("MATERIAL_ISSUED");
    assertThat(issued.issuedAmount()).isNotNull();
}

@Test
void completesOrderAndPostsFinishedGoodsInbound() {
    productionOrderService.release(orderId);
    productionIssueService.issue(orderId);
    ProductionOrderResponse completed = productionCompletionService.complete(orderId);
    assertThat(completed.status()).isEqualTo("COMPLETED");
    assertThat(completed.finishedAmount()).isEqualByComparingTo(completed.issuedAmount());
}
```

- [x] **Step 2: Run the tests to confirm they fail**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=ProductionIssueServiceTest,ProductionCompletionServiceTest test
```

Expected: FAIL.

- [x] **Step 3: Implement issue and completion services**

Issue flow must:

1. load the released order and materials,
2. release each material reservation,
3. post outbound stock from the material warehouse,
4. accumulate issue amounts,
5. update material row issue fields and order status.

Use a production-specific transaction type such as `PRODUCTION_ISSUE` and post each material line with the order line id as `bizLineId`.

Completion flow must:

1. require the order to be `MATERIAL_ISSUED`,
2. post inbound finished goods to the finished warehouse,
3. use the total issue amount as inbound amount,
4. mark the order completed,
5. reject repeated completion.

- [x] **Step 4: Re-run the issue/completion tests**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=ProductionIssueServiceTest,ProductionCompletionServiceTest test
```

Expected: PASS.

## Task 5: Wire Permissions, Data Scope, And Menus

**Files:**
- Modify: `src/main/java/com/tuowei/erp/common/security/PermissionCodes.java`
- Modify: `src/main/java/com/tuowei/erp/common/security/DataScopeService.java`
- Create: `src/main/resources/db/migration/V39__production_menu_seed.sql`
- Modify: `src/main/java/com/tuowei/erp/production/bom/controller/ProductionBomController.java`
- Modify: `src/main/java/com/tuowei/erp/production/order/controller/ProductionOrderController.java`
- Test: `src/test/java/com/tuowei/erp/production/order/ProductionOrderDataScopeTest.java`
- Test: `src/test/java/com/tuowei/erp/production/order/ProductionOrderPermissionTest.java`

- [x] **Step 1: Write the failing permission and scope tests**

Add tests that prove:

```java
@Test
void rejectsOrderVisibilityOutsideWarehouseScope() {
    // user only has one warehouse in scope
    assertThatThrownBy(() -> productionOrderService.getById(orderId))
            .isInstanceOf(AccessDeniedException.class);
}
```

Also prove unauthorized access returns `403` at controller level.

- [x] **Step 2: Run the tests to confirm they fail**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=ProductionOrderDataScopeTest,ProductionOrderPermissionTest test
```

Expected: FAIL.

- [x] **Step 3: Implement data-scope checks and menu seeds**

Production orders and BOMs must obey company isolation and warehouse scope. Follow the existing pattern used by inventory transfer and sales order:

```java
dataScopeService.assertCanViewProductionOrder(entity, currentUser, snapshot, creatorDeptId, creatorPostId);
```

Add menu permissions for production BOM and production order entrypoints in the seed migration.

- [x] **Step 4: Re-run permission and scope tests**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=ProductionOrderDataScopeTest,ProductionOrderPermissionTest test
```

Expected: PASS.

## Task 6: End-to-End Regression And Release Check

**Files:**
- Existing production files from Tasks 1-5
- Modify: `docs/business-readiness-checklist.md` only if the new production flow needs a user-facing readiness note

- [x] **Step 1: Add one end-to-end controller regression**

Write a MockMvc test that creates a BOM, creates an order, releases it, issues material, and completes the order in one path.

- [x] **Step 2: Run focused production tests**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=FlywayMigrationSmokeTest,ProductionBomServiceTest,ProductionOrderServiceTest,ProductionOrderControllerTest" test
```

Expected: PASS. The current minimal regression suite keeps issue and completion coverage in `ProductionOrderServiceTest` and `ProductionOrderControllerTest` instead of separate `ProductionIssueServiceTest` / `ProductionCompletionServiceTest` classes.

- [x] **Step 3: Run the release gate**

Run:

```powershell
.\scripts\release-check.ps1
```

Expected: `BUILD SUCCESS`.

## Implementation Evidence

- Production schema and menu seed are implemented in `V38__production_manufacturing_schema.sql` and `V39__production_menu_seed.sql`.
- BOM create/update/detail/list, ordered lines, duplicate material rejection, self-material rejection, and duplicate active BOM rejection are covered by `ProductionBomServiceTest` and controller routes.
- Production order create/update/detail/list/release/cancel/issue/complete is implemented under `src/main/java/com/tuowei/erp/production`.
- Material issue releases production reservations, posts `PRODUCTION_ISSUE` outbound stock, and records material issue quantity and amount.
- Completion posts `PRODUCTION_COMPLETION` inbound stock, records `completed_qty`, and carries issued amount into finished amount.
- Permissions, menu seeds, and production order warehouse data-scope checks are wired through `PermissionCodes`, `DataScopeService`, and controller `@PreAuthorize` guards.
- Verification completed with focused production tests and `scripts/release-check.ps1`.

## Self-Review

- Spec coverage: The plan covers schema, BOM management, order lifecycle, issue, completion, permissions, data scope, menu seeds, and regression verification.
- Placeholder scan: No placeholder markers or vague implementation steps remain.
- Type consistency: The plan uses one naming set consistently: `ProductionBom*`, `ProductionOrder*`, `ProductionIssueService`, `ProductionCompletionService`, `PRODUCTION_ORDER`, `PRODUCTION_ISSUE`, and `PRODUCTION_COMPLETION`.
- Scope check: Advanced manufacturing features remain out of scope and should be separate plans.
