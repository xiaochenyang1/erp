# Inventory Replenishment Suggestion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a replenishment suggestion workflow that lets users turn active low-stock alerts into draft purchase-order suggestions and then convert them into draft purchase orders.

**Architecture:** First fix the existing red quality gates so new failures are attributable to this feature. Backend adds a focused `inventory.replenishment` domain with a Flyway table, MyBatis entity/mapper, service, controller, permissions, and tests. Frontend extends `inventory.ts`, the inventory alert page, router, and a new replenishment suggestions page.

**Tech Stack:** Java 17, Spring Boot, MyBatis-Plus, Flyway, JUnit/Mockito/MockMvc, Vue 3, TypeScript, Element Plus.

---

### Task 1: Fix Existing Quality Gate Failures

**Files:**
- Modify: `src/main/java/com/tuowei/erp/finance/voucher/service/ManualVoucherService.java`
- Modify: `src/test/java/com/tuowei/erp/inventory/alert/InventoryAlertServiceTenantBoundaryTest.java`
- Modify: `E:/tuowei/python/erp-frontend/src/composables/useTablePreference.ts`
- Modify: `E:/tuowei/python/erp-frontend/src/components/common/TableColumnSetting.vue`

- [ ] **Step 1: Make `ManualVoucherService.list` null-safe**

Change the start of `list(ManualVoucherPageQuery query)` to use a safe query object:

```java
@Transactional(readOnly = true)
public PageResponse<ManualVoucherResponse> list(ManualVoucherPageQuery query) {
    ManualVoucherPageQuery safeQuery = query == null ? new ManualVoucherPageQuery() : query;
    AuditMetadata audit = auditMetadataFactory.current();
    Page<ManualVoucherEntity> page = new Page<>(safeQuery.getPageNo(), safeQuery.getPageSize());
    LambdaQueryWrapper<ManualVoucherEntity> wrapper = new LambdaQueryWrapper<ManualVoucherEntity>()
            .eq(ManualVoucherEntity::getCompanyId, audit.companyId())
            .eq(ManualVoucherEntity::getAccountBookId, audit.accountBookId())
            .eq(ManualVoucherEntity::getDeletedFlag, 0);
    if (StringUtils.hasText(safeQuery.getVoucherNo())) {
        wrapper.like(ManualVoucherEntity::getVoucherNo, safeQuery.getVoucherNo().trim());
    }
    if (StringUtils.hasText(safeQuery.getStatus())) {
        wrapper.eq(ManualVoucherEntity::getStatus, safeQuery.getStatus().trim());
    }
    if (safeQuery.getDateFrom() != null) {
        wrapper.ge(ManualVoucherEntity::getBizDate, safeQuery.getDateFrom());
    }
    if (safeQuery.getDateTo() != null) {
        wrapper.le(ManualVoucherEntity::getBizDate, safeQuery.getDateTo());
    }
```

- [ ] **Step 2: Update inventory alert service test constructor**

Ensure `InventoryAlertServiceTenantBoundaryTest.service()` passes `dispositionMapper` as the second constructor argument:

```java
private InventoryAlertService service() {
    return new InventoryAlertService(
            alertRuleMapper,
            dispositionMapper,
            inventoryPostingService,
            auditMetadataFactory,
            warehouseMapper,
            productMapper
    );
}
```

- [ ] **Step 3: Fix table preference typing and semicolon**

In `useTablePreference.ts`, add `hideable?: boolean`, loosen query generic to `object`, and remove the leading semicolon:

```ts
export interface TableColumnOption {
  prop: string
  label: string
  hideable?: boolean
}

export interface UseTablePreferenceOptions<Q extends object> {
  defaultSearchForm: Q
  persistentSearchKeys: Array<keyof Q>
  columns: TableColumnOption[]
}

export function useTablePreference<Q extends object>(
  storageKey: string,
  options: UseTablePreferenceOptions<Q>
) {
  // ...
  if (k in stored.query) {
    ;(searchForm as Record<string, unknown>)[k] = stored.query[k]
  }
}
```

Then remove the leading semicolon by wrapping the cast assignment:

```ts
if (k in stored.query) {
  const writableSearchForm = searchForm as Record<string, unknown>
  writableSearchForm[k] = stored.query[k]
}
```

- [ ] **Step 4: Stop mutating `modelValue` prop**

In `TableColumnSetting.vue`, add update emit and change computed setter:

```ts
const emit = defineEmits<{
  (e: 'reset'): void
  (e: 'update:modelValue', value: Record<string, boolean>): void
}>()

const visibleProps = computed<string[]>({
  get() {
    return hideableColumns.value
      .filter((col) => props.modelValue[col.prop] !== false)
      .map((col) => col.prop)
  },
  set(next) {
    const nextSet = new Set(next)
    const nextValue = { ...props.modelValue }
    for (const col of hideableColumns.value) {
      nextValue[col.prop] = nextSet.has(col.prop)
    }
    emit('update:modelValue', nextValue)
  }
})
```

- [ ] **Step 5: Run focused verification**

Run:

```powershell
.\mvnw.cmd -B "-Dtest=BusinessReadOnlyTransactionStructureTest,InventoryAlertServiceTenantBoundaryTest" test
```

Expected: `BUILD SUCCESS`.

Run in `E:/tuowei/python/erp-frontend`:

```powershell
npm run type-check
npm run lint
```

Expected: no TypeScript errors and no ESLint errors. Existing `no-console` warnings can remain for this task.

### Task 2: Backend Schema, Entity, Mapper, Permissions

**Files:**
- Create: `src/main/resources/db/migration/V87__inventory_replenishment_suggestion.sql`
- Modify: `docs/migrations-history.md`
- Modify: `src/main/java/com/tuowei/erp/common/security/InventoryPermissionCodes.java`
- Create: `src/main/java/com/tuowei/erp/inventory/replenishment/model/InventoryReplenishmentSuggestionEntity.java`
- Create: `src/main/java/com/tuowei/erp/inventory/replenishment/mapper/InventoryReplenishmentSuggestionMapper.java`
- Create: `src/main/java/com/tuowei/erp/inventory/replenishment/web/InventoryReplenishmentSuggestionPageQuery.java`
- Create: `src/main/java/com/tuowei/erp/inventory/replenishment/web/InventoryReplenishmentSuggestionCreateRequest.java`
- Create: `src/main/java/com/tuowei/erp/inventory/replenishment/web/InventoryReplenishmentSuggestionCancelRequest.java`
- Create: `src/main/java/com/tuowei/erp/inventory/replenishment/web/InventoryReplenishmentSuggestionResponse.java`

- [ ] **Step 1: Write schema migration**

Create `V87__inventory_replenishment_suggestion.sql`:

```sql
CREATE TABLE IF NOT EXISTS inv_replenishment_suggestion (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    suggestion_no VARCHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_rule_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    supplier_id BIGINT,
    suggested_qty DECIMAL(20, 4) NOT NULL,
    shortage_qty_snapshot DECIMAL(20, 4) NOT NULL DEFAULT 0,
    expected_arrival_date DATE,
    status VARCHAR(16) NOT NULL,
    purchase_order_id BIGINT,
    purchase_order_no VARCHAR(64),
    remark VARCHAR(512),
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_inv_replenishment_suggestion_status CHECK (status IN ('DRAFT', 'CONVERTED', 'CANCELLED'))
);

CREATE UNIQUE INDEX uk_inv_replenishment_suggestion_no
    ON inv_replenishment_suggestion (company_id, account_book_id, suggestion_no);

CREATE UNIQUE INDEX uk_inv_replenishment_suggestion_active_source
    ON inv_replenishment_suggestion (company_id, account_book_id, source_type, warehouse_id, product_id, status, deleted_flag);

CREATE INDEX idx_inv_replenishment_suggestion_query
    ON inv_replenishment_suggestion (company_id, account_book_id, status, warehouse_id, product_id, supplier_id, deleted_flag);

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5104, 505, 'MENU', 'INVENTORY_REPLENISHMENT', '补货建议', '/inventory/replenishment-suggestions',
     'inventory/replenishment-suggestions/index', 'inventory:replenishment:view', 6, 1, 'ACTIVE', 0, 0, 0, 0),
    (5105, 5104, 'BUTTON', 'INVENTORY_REPLENISHMENT_CREATE', '生成补货建议', NULL, NULL,
     'inventory:replenishment:create', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5106, 5104, 'BUTTON', 'INVENTORY_REPLENISHMENT_CANCEL', '取消补货建议', NULL, NULL,
     'inventory:replenishment:cancel', 2, 1, 'ACTIVE', 0, 0, 0, 0),
    (5107, 5104, 'BUTTON', 'INVENTORY_REPLENISHMENT_CONVERT', '转采购订单', NULL, NULL,
     'inventory:replenishment:convert', 3, 1, 'ACTIVE', 0, 0, 0, 0)
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
```

- [ ] **Step 2: Add permission constants**

Add to `InventoryPermissionCodes.java`:

```java
String INVENTORY_REPLENISHMENT_VIEW = "inventory:replenishment:view";
String INVENTORY_REPLENISHMENT_CREATE = "inventory:replenishment:create";
String INVENTORY_REPLENISHMENT_CANCEL = "inventory:replenishment:cancel";
String INVENTORY_REPLENISHMENT_CONVERT = "inventory:replenishment:convert";

String HAS_INVENTORY_REPLENISHMENT_VIEW = "hasAuthority('" + INVENTORY_REPLENISHMENT_VIEW + "')";
String HAS_INVENTORY_REPLENISHMENT_CREATE = "hasAuthority('" + INVENTORY_REPLENISHMENT_CREATE + "')";
String HAS_INVENTORY_REPLENISHMENT_CANCEL = "hasAuthority('" + INVENTORY_REPLENISHMENT_CANCEL + "')";
String HAS_INVENTORY_REPLENISHMENT_CONVERT = "hasAuthority('" + INVENTORY_REPLENISHMENT_CONVERT + "')";
```

- [ ] **Step 3: Add entity and mapper**

Create `InventoryReplenishmentSuggestionEntity` with `@TableName("inv_replenishment_suggestion")`, `@TableId(type = IdType.ASSIGN_ID)`, Java fields matching the SQL columns, getters/setters, and `@Version private Integer version`.

Create mapper:

```java
@Mapper
public interface InventoryReplenishmentSuggestionMapper extends BaseMapper<InventoryReplenishmentSuggestionEntity> {
}
```

- [ ] **Step 4: Add web records**

Create page query with `pageNo`, `pageSize`, `suggestionNo`, `status`, `warehouseId`, `productId`, `supplierId`, `createdTimeFrom`, and `createdTimeTo`.

Create request records:

```java
public record InventoryReplenishmentSuggestionCreateRequest(
        @NotNull Long ruleId,
        @NotNull Long warehouseId,
        @NotNull Long productId,
        Long supplierId,
        @NotNull @DecimalMin("0.0001") BigDecimal suggestedQty,
        LocalDate expectedArrivalDate,
        String remark
) {
}

public record InventoryReplenishmentSuggestionCancelRequest(String reason) {
}
```

Create response record with all user-facing fields from the table plus `warehouseName`, `productCode`, `productName`, and `supplierName`.

### Task 3: Backend Service Tests

**Files:**
- Create: `src/test/java/com/tuowei/erp/inventory/replenishment/InventoryReplenishmentSuggestionServiceTest.java`

- [ ] **Step 1: Write create test**

Test behavior:

- Mock current audit metadata.
- Mock active alert rule with `minQty=10`.
- Mock current qty on hand as `3`.
- Mock no existing draft suggestion.
- Call `create`.
- Assert inserted suggestion has `suggestedQty=7`, `shortageQtySnapshot=7`, `status=DRAFT`, `sourceType=LOW_STOCK_ALERT`.
- Verify `InventoryAlertService.handle(warehouseId, productId, "RESOLVED", ...)` is called.

- [ ] **Step 2: Write duplicate draft rejection test**

Mock an existing `DRAFT` suggestion for same tenant, source type, warehouse, and product. Expected exception message: `已存在待处理补货建议`.

- [ ] **Step 3: Write cancel transition tests**

Verify `DRAFT -> CANCELLED` updates status and remark. Verify `CONVERTED` cannot be cancelled and throws `当前补货建议状态不允许取消`.

- [ ] **Step 4: Write convert tests**

Mock a draft suggestion, active supplier, and `PurchaseOrderService.create`. Verify:

- Purchase order create request has one line.
- Line has `productId`, `qty=suggestedQty`, `price=0`, `taxRate=0`.
- Suggestion is updated to `CONVERTED` with returned purchase order id and number.
- Converted suggestion cannot be converted again.

- [ ] **Step 5: Run red test**

Run:

```powershell
.\mvnw.cmd -B "-Dtest=InventoryReplenishmentSuggestionServiceTest" test
```

Expected before implementation: compilation failure because service classes do not exist.

### Task 4: Backend Service And Controller

**Files:**
- Create: `src/main/java/com/tuowei/erp/inventory/replenishment/service/InventoryReplenishmentSuggestionService.java`
- Create: `src/main/java/com/tuowei/erp/inventory/replenishment/controller/InventoryReplenishmentSuggestionController.java`

- [ ] **Step 1: Implement service dependencies**

Inject:

```java
InventoryReplenishmentSuggestionMapper suggestionMapper
InventoryAlertRuleMapper alertRuleMapper
InventoryPostingService inventoryPostingService
InventoryAlertService inventoryAlertService
AuditMetadataFactory auditMetadataFactory
WarehouseMapper warehouseMapper
ProductMapper productMapper
SupplierMapper supplierMapper
PurchaseOrderService purchaseOrderService
```

- [ ] **Step 2: Implement `create`**

Rules:

- Current audit scopes all queries.
- Alert rule must exist, enabled, undeleted, and match request `ruleId`, `warehouseId`, and `productId`.
- Current qty on hand must be below rule `minQty`.
- Suggested quantity must be positive.
- No existing `DRAFT` suggestion for same source type, warehouse, product.
- Insert `DRAFT`.
- Suggestion number format for first version: `RS` + `yyyyMMdd` + six-digit daily count from existing rows. If sequence rule support is added later, replace this local generator.
- Call `inventoryAlertService.handle(warehouseId, productId, "RESOLVED", "已生成补货建议 " + suggestionNo)`.

- [ ] **Step 3: Implement `list`**

Use null-safe query defaults:

- `pageNo`: default 1
- `pageSize`: default 20, max 200
- Filter by tenant, account book, deleted flag.
- Optional filters: suggestion no fuzzy, status exact uppercase, warehouse id, product id, supplier id, created time range.
- Load display names with batch `selectBatchIds`.

- [ ] **Step 4: Implement `cancel`**

Only `DRAFT` can cancel. Store status `CANCELLED`, append cancel reason to remark, update audit fields, and use optimistic lock guard.

- [ ] **Step 5: Implement `convertToPurchaseOrder`**

Only `DRAFT` can convert. Supplier is required at conversion time. Call:

```java
purchaseOrderService.create(new PurchaseOrderCreateRequest(
        suggestion.getSupplierId(),
        LocalDate.now(clockOrAuditDate),
        suggestion.getExpectedArrivalDate(),
        "由补货建议 " + suggestion.getSuggestionNo() + " 生成。" + nullSafeRemark,
        List.of(new PurchaseOrderLineRequest(
                suggestion.getProductId(),
                suggestion.getSuggestedQty(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "补货建议 " + suggestion.getSuggestionNo()
        ))
));
```

Then set status `CONVERTED`, `purchaseOrderId`, and `purchaseOrderNo`.

- [ ] **Step 6: Implement controller**

Endpoints:

```java
@GetMapping("/api/inventory/replenishment-suggestions")
@PreAuthorize(PermissionCodes.HAS_INVENTORY_REPLENISHMENT_VIEW)

@PostMapping("/api/inventory/replenishment-suggestions")
@PreAuthorize(PermissionCodes.HAS_INVENTORY_REPLENISHMENT_CREATE)

@PostMapping("/api/inventory/replenishment-suggestions/{id}/cancel")
@PreAuthorize(PermissionCodes.HAS_INVENTORY_REPLENISHMENT_CANCEL)

@PostMapping("/api/inventory/replenishment-suggestions/{id}/convert-to-purchase-order")
@PreAuthorize(PermissionCodes.HAS_INVENTORY_REPLENISHMENT_CONVERT)
```

- [ ] **Step 7: Run focused backend tests**

Run:

```powershell
.\mvnw.cmd -B "-Dtest=InventoryReplenishmentSuggestionServiceTest" test
```

Expected: `BUILD SUCCESS`.

### Task 5: Frontend API, Route, And Pages

**Files:**
- Modify: `E:/tuowei/python/erp-frontend/src/api/inventory.ts`
- Modify: `E:/tuowei/python/erp-frontend/src/router/index.ts`
- Modify: `E:/tuowei/python/erp-frontend/src/views/inventory/alerts/index.vue`
- Create: `E:/tuowei/python/erp-frontend/src/views/inventory/replenishment-suggestions/index.vue`

- [ ] **Step 1: Add API types and functions**

In `inventory.ts`, add:

```ts
export interface InventoryReplenishmentSuggestion {
  id: string
  suggestionNo: string
  sourceType: string
  sourceRuleId: string
  warehouseId: string
  warehouseName?: string
  productId: string
  productCode?: string
  productName?: string
  supplierId?: string
  supplierName?: string
  suggestedQty: number
  shortageQtySnapshot: number
  expectedArrivalDate?: string
  status: 'DRAFT' | 'CONVERTED' | 'CANCELLED'
  purchaseOrderId?: string
  purchaseOrderNo?: string
  remark?: string
  createdTime?: string
}

export interface InventoryReplenishmentSuggestionQuery extends PageQuery {
  suggestionNo?: string
  status?: string
  warehouseId?: string | number
  productId?: string | number
  supplierId?: string | number
  createdTimeFrom?: string
  createdTimeTo?: string
}

export interface InventoryReplenishmentSuggestionCreateRequest {
  ruleId: string | number
  warehouseId: string | number
  productId: string | number
  supplierId?: string | number
  suggestedQty: number
  expectedArrivalDate?: string
  remark?: string
}
```

Add `getInventoryReplenishmentSuggestions`, `createInventoryReplenishmentSuggestion`, `cancelInventoryReplenishmentSuggestion`, and `convertInventoryReplenishmentSuggestion`.

- [ ] **Step 2: Add route**

Under inventory children in `router/index.ts`, add:

```ts
{
  path: 'replenishment-suggestions',
  name: 'InventoryReplenishmentSuggestions',
  component: () => import('@/views/inventory/replenishment-suggestions/index.vue'),
  meta: {
    title: '补货建议',
    icon: 'ShoppingCart',
    permission: 'inventory:replenishment:view'
  }
}
```

- [ ] **Step 3: Extend alert page**

Add `生成补货建议` action for `ACTIVE` alerts. Dialog fields:

- warehouse/product readonly display
- suggested qty default `row.shortageQty`
- supplier select from `getSuppliers({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })`
- expected arrival date
- remark

Submit calls `createInventoryReplenishmentSuggestion`.

- [ ] **Step 4: Add suggestion list page**

Build a compact Element Plus page:

- Filter form.
- Table.
- `取消` button for `DRAFT`.
- `转采购订单` button for `DRAFT`.
- Link to `/purchase/orders` filtered by generated purchase order number for `CONVERTED`.

- [ ] **Step 5: Run frontend checks**

Run in `E:/tuowei/python/erp-frontend`:

```powershell
npm run type-check
npm run lint
npm run build
```

Expected: exit code 0 for all three.

### Task 6: Final Verification

**Files:**
- Read every file changed in Tasks 1-5.

- [ ] **Step 1: Run focused backend tests**

Run:

```powershell
.\mvnw.cmd -B "-Dtest=BusinessReadOnlyTransactionStructureTest,InventoryAlertServiceTenantBoundaryTest,InventoryReplenishmentSuggestionServiceTest" test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Run backend full test**

Run:

```powershell
.\mvnw.cmd -B test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Run frontend checks**

Run:

```powershell
npm run type-check
npm run lint
npm run build
```

Expected: all pass.

- [ ] **Step 4: Review migration and permissions**

Verify:

- `V87__inventory_replenishment_suggestion.sql` is listed in `docs/migrations-history.md`.
- `PermissionCodes.allPermissions()` sees new inventory replenishment permissions through `InventoryPermissionCodes`.
- Route permission matches backend permission: `inventory:replenishment:view`.

- [ ] **Step 5: Manual smoke**

With backend and frontend running:

- Create or use an active low-stock rule where current stock is below minimum.
- Open `/inventory/alerts`.
- Click `生成补货建议`.
- Save draft suggestion.
- Open `/inventory/replenishment-suggestions`.
- Convert the draft suggestion.
- Confirm generated purchase order number is shown and the purchase order exists.
