# Inventory Lot Genealogy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a read-only lot genealogy query and page that answers, for one lot, which suppliers it came from and which customers received it — traversing across production orders in both directions.

**Architecture:** A new `InventoryLotGenealogyService` walks `inv_txn` one level at a time. Each level issues exactly one batched transaction query for every lot reached so far, plus one batched counter-side query to cross the manufacturing boundary, plus batched counterparty and display lookups. No schema change: `PRODUCTION_ISSUE` and `PRODUCTION_COMPLETION` already write the production order number as `biz_no` alongside `lot_no`, and that shared key is the genealogy edge. Counterparty resolution and display hydration live in two separate collaborators so the traversal service keeps six constructor dependencies instead of twelve.

**Tech Stack:** Java 17, Spring Boot 3.5.14, MyBatis-Plus (`LambdaQueryWrapper`, no native SQL), Flyway, JUnit 5 + Mockito + AssertJ. Frontend: Vue 3 + TypeScript, Element Plus, Vitest, vue-i18n.

**Spec:** `backend/docs/superpowers/specs/2026-08-17-inventory-lot-genealogy-design.md` (read it first — it carries the approved approach, the rejected alternatives, and the security decision)

## Global Constraints

- **No native SQL.** Every query uses `LambdaQueryWrapper`. `NativeSqlTenantScopeConfigurationTest` guards this; hand-written SQL would have to inject tenant and data scope itself.
- **Tenant filtering on every query, at every level.** Always `eq(companyId)` and `eq(accountBookId)` from `CurrentUser`, and always `dataScopeService.applyInventoryTransactionScope(wrapper, snapshot)` — not only on the first level.
- **`inv_txn.direction` values are exactly `"IN"` and `"OUT"`** (string literals; there is no enum in the codebase).
- **No silent truncation.** Every cap that fires must appear in `limits.truncationReasons`. A tree that looks complete must be complete.
- **`scopeLimited` = `!snapshot.hasAllScope()`**, set whenever the caller's snapshot is not ALL.
- **Guard defaults:** `maxDepth` default `5`, clamped to `[1, 10]`. `perLevelNodeLimit` = `200`. `totalNodeLimit` = `500`, counted per direction.
- **Terminal reason closed set:** `PURCHASED`, `SOLD`, `RETURNED_BY_CUSTOMER`, `RETURNED_TO_SUPPLIER`, `MOVED_INTERNALLY`, `ADJUSTED`, `OPENING_BALANCE`, `REVERSED`, `IN_PRODUCTION`, `NO_MATERIAL_ISSUED`, `MATERIAL_NOT_LOT_CONTROLLED`, `OUTPUT_NOT_LOT_CONTROLLED`, `ALREADY_VISITED`, `MAX_DEPTH`, `UNKNOWN_SOURCE`, `UNKNOWN_DESTINATION`.
- **Existing behavior is frozen.** `GET /api/inventory/lots/trace`, its response bytes, its dialog, and every existing test must behave identically after this change.
- **Permission code is exactly `inventory:lot:genealogy`** — no `:view` suffix, matching the approved design. The seeded migration value and the constant must agree.
- **Frontend: no hardcoded Chinese in templates.** All copy goes through i18n with `zh-CN` and `en-US` at full key parity; English values must contain no CJK characters (`modular-page-messages.test.ts` enforces both).
- **Backend test command:** `cd backend && ./mvnw test -Dtest=<TestClass>` (tests run against MySQL via `ERP_TEST_DATASOURCE_URL`, default `jdbc:mysql://localhost:3306/erp_codex_test`).
- **Frontend test command:** `cd frontend && npx vitest run <path>`.

---

## File Structure

**Backend — create:**

| Path | Responsibility |
|---|---|
| `inventory/stock/web/InventoryLotGenealogyQuery.java` | Request bean (getters/setters, Spring param binding) |
| `inventory/stock/web/InventoryLotGenealogyResponse.java` | Top-level record: root, upstream, downstream, limits |
| `inventory/stock/web/LotGenealogyNode.java` | One lot in the tree |
| `inventory/stock/web/LotGenealogyLink.java` | One document movement out of a node |
| `inventory/stock/web/CounterpartyRef.java` | Supplier or customer identity |
| `inventory/stock/web/GenealogyLimits.java` | Guard values and what fired |
| `inventory/stock/service/InventoryDocumentLinkResolver.java` | `biz_type` → route and label (extracted from `InventoryLotQueryService`) |
| `inventory/stock/service/LotGenealogyCounterpartyResolver.java` | Batched receipt→PO→supplier and delivery→SO→customer resolution |
| `inventory/stock/service/LotGenealogyDisplayResolver.java` | Batched product and warehouse display hydration |
| `inventory/stock/service/InventoryLotGenealogyService.java` | The level-batched traversal |
| `resources/db/migration/V145__inventory_lot_genealogy_menu.sql` | Menu node + role binding |

**Backend — modify:**

| Path | Change |
|---|---|
| `inventory/stock/service/InventoryLotQueryService.java` | Delete the two private resolvers, inject `InventoryDocumentLinkResolver` |
| `inventory/stock/controller/InventoryStockQueryController.java` | Add `GET /lots/genealogy` |
| `common/security/InventoryPermissionCodes.java` | Add `INVENTORY_LOT_GENEALOGY` + `HAS_INVENTORY_LOT_GENEALOGY` |

**Backend — tests:** `InventoryDocumentLinkResolverTest`, `InventoryLotGenealogyServiceTest`, `InventoryLotGenealogyControllerTest`, `db/InventoryLotGenealogyMenuMigrationTest`.

**Frontend — create:** `composables/useInventoryLotGenealogyPresentation.ts`, `useInventoryLotGenealogyTree.ts`, `useInventoryLotGenealogyQuery.ts` (+ a `.test.ts` beside each), `views/inventory/lot-genealogy/index.vue`.

**Frontend — modify:** `api/inventory.ts`, `router/index.ts`, `i18n/operations-pages.ts`, `i18n/operations-pages.test.ts`, `views/inventory/stocks/index.vue`.

---

## Task 1: Extract InventoryDocumentLinkResolver

Pure behavior-preserving refactor. Does the genealogy no good if `traceLot` regresses, so it lands first and alone.

**Files:**
- Create: `backend/src/main/java/com/tuowei/erp/inventory/stock/service/InventoryDocumentLinkResolver.java`
- Modify: `backend/src/main/java/com/tuowei/erp/inventory/stock/service/InventoryLotQueryService.java` (delete private `resolveDocumentRoute` / `resolveDocumentLabel`, inject the new component)
- Test: `backend/src/test/java/com/tuowei/erp/inventory/stock/InventoryDocumentLinkResolverTest.java`

**Interfaces:**
- Produces: `InventoryDocumentLinkResolver.resolveRoute(String bizType, String bizNo) → String` (nullable), `InventoryDocumentLinkResolver.resolveLabel(String bizType) → String` (nullable; falls back to the raw `bizType`). Every later backend task consumes these.

- [ ] **Step 1: Write the failing test**

`InventoryDocumentLinkResolverTest.java`:

```java
package com.tuowei.erp.inventory.stock;

import com.tuowei.erp.inventory.stock.service.InventoryDocumentLinkResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryDocumentLinkResolverTest {

    private final InventoryDocumentLinkResolver resolver = new InventoryDocumentLinkResolver();

    @Test
    void resolvesRoutesForEveryTraversedBizType() {
        assertThat(resolver.resolveRoute("PURCHASE_RECEIPT", "PR-1")).isEqualTo("/purchase/receipts?keyword=PR-1");
        assertThat(resolver.resolveRoute("PURCHASE_RETURN", "PRT-1")).isEqualTo("/purchase/returns?keyword=PRT-1");
        assertThat(resolver.resolveRoute("SALES_DELIVERY", "SD-1")).isEqualTo("/sales/deliveries?keyword=SD-1");
        assertThat(resolver.resolveRoute("SALES_RETURN", "SR-1")).isEqualTo("/sales/returns?keyword=SR-1");
        assertThat(resolver.resolveRoute("PRODUCTION_ISSUE", "MO-1")).isEqualTo("/production/orders?keyword=MO-1");
        assertThat(resolver.resolveRoute("PRODUCTION_COMPLETION", "MO-1")).isEqualTo("/production/orders?keyword=MO-1");
        assertThat(resolver.resolveRoute("PRODUCTION_COMPLETION_REVERSAL", "MO-1")).isEqualTo("/production/orders?keyword=MO-1");
        assertThat(resolver.resolveRoute("PRODUCTION_RETURN", "MO-1")).isEqualTo("/production/orders?keyword=MO-1");
        assertThat(resolver.resolveRoute("INVENTORY_ADJUSTMENT", "IA-1")).isEqualTo("/inventory/adjustments?keyword=IA-1");
        assertThat(resolver.resolveRoute("INVENTORY_TRANSFER", "IT-1")).isEqualTo("/inventory/transfers?keyword=IT-1");
        assertThat(resolver.resolveRoute("INVENTORY_CHECK", "IC-1")).isEqualTo("/inventory/checks?keyword=IC-1");
        assertThat(resolver.resolveRoute("OPENING_INVENTORY", "OB-1"))
                .isEqualTo("/system/imports?importType=OPENING_INVENTORY&keyword=OB-1");
    }

    @Test
    void urlEncodesDocumentNumbersAndNormalizesCase() {
        assertThat(resolver.resolveRoute("purchase_receipt", " PR 1 ")).isEqualTo("/purchase/receipts?keyword=PR+1");
    }

    @Test
    void returnsNullRouteForBlankOrUnknownInput() {
        assertThat(resolver.resolveRoute(null, "X")).isNull();
        assertThat(resolver.resolveRoute("PURCHASE_RECEIPT", " ")).isNull();
        assertThat(resolver.resolveRoute("MYSTERY_TYPE", "X-1")).isNull();
    }

    @Test
    void labelsFallBackToRawBizType() {
        assertThat(resolver.resolveLabel("PRODUCTION_COMPLETION")).isEqualTo("生产完工");
        assertThat(resolver.resolveLabel("MYSTERY_TYPE")).isEqualTo("MYSTERY_TYPE");
        assertThat(resolver.resolveLabel(" ")).isNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=InventoryDocumentLinkResolverTest`
Expected: compilation failure — `InventoryDocumentLinkResolver` does not exist.

- [ ] **Step 3: Create the component by moving the two methods verbatim**

Create `InventoryDocumentLinkResolver.java`. Copy the bodies of `resolveDocumentRoute` and `resolveDocumentLabel` out of `InventoryLotQueryService` **unchanged** — same switch arms, same Chinese labels, same `URLEncoder` call — renaming only the methods and making them public:

```java
package com.tuowei.erp.inventory.stock.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Maps an {@code inv_txn.biz_type} to the frontend route and display label for its source document.
 * Extracted from {@link InventoryLotQueryService} so lot trace and lot genealogy share one mapping.
 */
@Component
public class InventoryDocumentLinkResolver {

    public String resolveRoute(String bizType, String bizNo) {
        if (!StringUtils.hasText(bizType) || !StringUtils.hasText(bizNo)) {
            return null;
        }
        String type = bizType.trim().toUpperCase(Locale.ROOT);
        String encoded = URLEncoder.encode(bizNo.trim(), StandardCharsets.UTF_8);
        return switch (type) {
            case "PURCHASE_RECEIPT" -> "/purchase/receipts?keyword=" + encoded;
            case "PURCHASE_RETURN" -> "/purchase/returns?keyword=" + encoded;
            case "SALES_DELIVERY" -> "/sales/deliveries?keyword=" + encoded;
            case "SALES_RETURN" -> "/sales/returns?keyword=" + encoded;
            case "PRODUCTION_ISSUE", "PRODUCTION_COMPLETION", "PRODUCTION_COMPLETION_REVERSAL", "PRODUCTION_RETURN" ->
                    "/production/orders?keyword=" + encoded;
            case "INVENTORY_ADJUSTMENT" -> "/inventory/adjustments?keyword=" + encoded;
            case "INVENTORY_TRANSFER" -> "/inventory/transfers?keyword=" + encoded;
            case "INVENTORY_CHECK" -> "/inventory/checks?keyword=" + encoded;
            case "OPENING_INVENTORY", "OPENING_BALANCE" ->
                    "/system/imports?importType=OPENING_INVENTORY&keyword=" + encoded;
            default -> null;
        };
    }

    public String resolveLabel(String bizType) {
        if (!StringUtils.hasText(bizType)) {
            return null;
        }
        return switch (bizType.trim().toUpperCase(Locale.ROOT)) {
            case "PURCHASE_RECEIPT" -> "采购收货";
            case "PURCHASE_RETURN" -> "采购退货";
            case "SALES_DELIVERY" -> "销售发货";
            case "SALES_RETURN" -> "销售退货";
            case "PRODUCTION_ISSUE" -> "生产领料";
            case "PRODUCTION_COMPLETION" -> "生产完工";
            case "PRODUCTION_COMPLETION_REVERSAL" -> "完工红冲";
            case "PRODUCTION_RETURN" -> "生产退料";
            case "INVENTORY_ADJUSTMENT" -> "库存调整";
            case "INVENTORY_TRANSFER" -> "库存调拨";
            case "INVENTORY_CHECK" -> "库存盘点";
            case "OPENING_INVENTORY", "OPENING_BALANCE" -> "期初库存";
            default -> bizType;
        };
    }
}
```

- [ ] **Step 4: Rewire `InventoryLotQueryService`**

Add the constructor parameter `InventoryDocumentLinkResolver documentLinkResolver` (assign to a new final field), delete the two private methods, and change the two call sites in `toLotTraceResponse` to `documentLinkResolver.resolveRoute(bizType, entity.getBizNo())` and `documentLinkResolver.resolveLabel(bizType)`. Remove the now-unused `URLEncoder`, `StandardCharsets`, and — only if nothing else uses it — `Locale` imports.

Then fix the two existing constructions of the service: `InventoryLotQueryServiceTest` and wherever `InventoryStockQueryService` builds or injects it. Pass a real `new InventoryDocumentLinkResolver()` in the test (it has no dependencies, so mocking it would only make the test lie about label output).

- [ ] **Step 5: Run the new test and the frozen ones together**

Run: `cd backend && ./mvnw test -Dtest='InventoryDocumentLinkResolverTest+InventoryLotQueryServiceTest+InventoryLotBalanceQueryTest+InventoryLotDomainIntegrationTest'`
Expected: PASS, all four. `InventoryLotQueryServiceTest` passing unchanged is the proof that `traceLot` output did not move.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/tuowei/erp/inventory/stock/service/InventoryDocumentLinkResolver.java \
        backend/src/main/java/com/tuowei/erp/inventory/stock/service/InventoryLotQueryService.java \
        backend/src/test/java/com/tuowei/erp/inventory/stock/InventoryDocumentLinkResolverTest.java \
        backend/src/test/java/com/tuowei/erp/inventory/stock/InventoryLotQueryServiceTest.java
git commit -m "refactor: extract inventory document link resolver"
```

---

## Task 2: Genealogy DTOs, validation, and the empty-history root

Establishes the response shape and the two rejection paths. Ends with a service that answers correctly for a lot with no history — the spec's "empty-link root, not a 404" acceptance criterion.

**Files:**
- Create: `web/InventoryLotGenealogyQuery.java`, `web/CounterpartyRef.java`, `web/LotGenealogyLink.java`, `web/LotGenealogyNode.java`, `web/GenealogyLimits.java`, `web/InventoryLotGenealogyResponse.java`, `service/LotGenealogyDisplayResolver.java`, `service/InventoryLotGenealogyService.java`
- Test: `backend/src/test/java/com/tuowei/erp/inventory/stock/InventoryLotGenealogyServiceTest.java`

**Interfaces:**
- Consumes: `InventoryDocumentLinkResolver` (Task 1).
- Produces:
  - `InventoryLotGenealogyService.genealogy(InventoryLotGenealogyQuery) → InventoryLotGenealogyResponse`
  - `LotGenealogyDisplayResolver.products(Collection<Long>) → Map<Long, ProductDisplay>` where `ProductDisplay` is `record ProductDisplay(String code, String name)`; `LotGenealogyDisplayResolver.warehouseNames(Collection<Long>) → Map<Long, String>`
  - Record component names, relied on by every later task and by the frontend types: `LotGenealogyNode(Long productId, String productCode, String productName, String lotNo, LocalDate productionDate, LocalDate expiryDate, int depth, List<LotGenealogyLink> links)`; `LotGenealogyLink(String bizType, String bizNo, String bizLabel, String documentRoute, LocalDateTime occurredTime, BigDecimal qty, Long warehouseId, String warehouseName, CounterpartyRef counterparty, String terminalReason, LotGenealogyNode node)`; `CounterpartyRef(String type, Long id, String code, String name, String documentNo)`; `GenealogyLimits(int maxDepth, int perLevelNodeLimit, int totalNodeLimit, boolean truncated, List<String> truncationReasons, boolean scopeLimited)`; `InventoryLotGenealogyResponse(LotGenealogyNode root, LotGenealogyNode upstream, LotGenealogyNode downstream, GenealogyLimits limits)`

- [ ] **Step 1: Write the failing test**

Create `InventoryLotGenealogyServiceTest.java`. This file grows across Tasks 2–7; start with the fixture and the validation/empty cases.

```java
package com.tuowei.erp.inventory.stock;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import com.tuowei.erp.inventory.stock.service.InventoryDocumentLinkResolver;
import com.tuowei.erp.inventory.stock.service.InventoryLotGenealogyService;
import com.tuowei.erp.inventory.stock.service.LotGenealogyCounterpartyResolver;
import com.tuowei.erp.inventory.stock.service.LotGenealogyDisplayResolver;
import com.tuowei.erp.inventory.stock.web.InventoryLotGenealogyQuery;
import com.tuowei.erp.inventory.stock.web.InventoryLotGenealogyResponse;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryLotGenealogyServiceTest {

    private static final CurrentUser USER = new CurrentUser(
            9401L, 101L, 202L, 11L, 12L, "genealogy_user", "谱系用户");
    private static final DataScopeSnapshot ALL_SCOPE = DataScopeSnapshot.all();

    @Mock
    private InventoryTransactionMapper transactionMapper;
    @Mock
    private CurrentUserContext currentUserContext;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private LotGenealogyCounterpartyResolver counterpartyResolver;
    @Mock
    private LotGenealogyDisplayResolver displayResolver;

    private InventoryLotGenealogyService service;

    @BeforeAll
    static void initTableInfo() {
        // MyBatis-Plus needs table metadata before LambdaQueryWrapper can resolve method references.
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, InventoryTransactionEntity.class);
        TableInfoHelper.initTableInfo(assistant, ProductEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = new InventoryLotGenealogyService(
                transactionMapper, currentUserContext, dataScopeService,
                new InventoryDocumentLinkResolver(), counterpartyResolver, displayResolver);
        when(currentUserContext.requireCurrentUser()).thenReturn(USER);
        when(currentUserContext.requirePrincipal()).thenReturn(principal(ALL_SCOPE));
        // The scope helper is a pass-through in tests; Task 7 asserts it is called per level.
        when(dataScopeService.applyInventoryTransactionScope(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(displayResolver.products(any())).thenReturn(java.util.Map.of(
                7001L, new LotGenealogyDisplayResolver.ProductDisplay("P-7001", "成品甲")));
        when(displayResolver.warehouseNames(any())).thenReturn(java.util.Map.of(1L, "主仓"));
        when(counterpartyResolver.resolve(any(), any(), any(), any()))
                .thenReturn(LotGenealogyCounterpartyResolver.CounterpartyIndex.empty());
    }

    private static ErpPrincipal principal(DataScopeSnapshot snapshot) {
        return new ErpPrincipal(USER.userId(), USER.companyId(), USER.accountBookId(),
                USER.deptId(), USER.postId(), USER.username(), USER.realName(), "N/A", Set.of(), snapshot);
    }

    private static InventoryLotGenealogyQuery query(Long productId, String lotNo) {
        InventoryLotGenealogyQuery query = new InventoryLotGenealogyQuery();
        query.setProductId(productId);
        query.setLotNo(lotNo);
        return query;
    }

    static InventoryTransactionEntity txn(
            Long productId, String lotNo, String bizType, String bizNo, String direction) {
        InventoryTransactionEntity entity = new InventoryTransactionEntity();
        entity.setId((long) (bizNo.hashCode() & 0xffff));
        entity.setCompanyId(USER.companyId());
        entity.setAccountBookId(USER.accountBookId());
        entity.setWarehouseId(1L);
        entity.setProductId(productId);
        entity.setLotNo(lotNo);
        entity.setBizType(bizType);
        entity.setBizNo(bizNo);
        entity.setDirection(direction);
        entity.setQty(new BigDecimal("10"));
        entity.setOccurredTime(LocalDateTime.of(2026, 8, 1, 10, 0));
        entity.setProductionDate(LocalDate.of(2026, 7, 1));
        entity.setExpiryDate(LocalDate.of(2027, 7, 1));
        return entity;
    }

    @Test
    void rejectsMissingProductId() {
        assertThatThrownBy(() -> service.genealogy(query(null, "LOT-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("批次谱系必须指定商品");
    }

    @Test
    void rejectsBlankLotNo() {
        assertThatThrownBy(() -> service.genealogy(query(7001L, "   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("批次谱系必须指定批次号");
    }

    @Test
    void rejectsUnknownDirection() {
        InventoryLotGenealogyQuery request = query(7001L, "LOT-1");
        request.setDirection("SIDEWAYS");
        assertThatThrownBy(() -> service.genealogy(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("批次谱系方向只支持 UPSTREAM、DOWNSTREAM 或 BOTH");
    }

    @Test
    void returnsEmptyLinkRootWhenLotHasNoHistory() {
        when(transactionMapper.selectList(any())).thenReturn(List.of());

        InventoryLotGenealogyResponse response = service.genealogy(query(7001L, " LOT-1 "));

        assertThat(response.root().productId()).isEqualTo(7001L);
        assertThat(response.root().lotNo()).isEqualTo("LOT-1");
        assertThat(response.root().productCode()).isEqualTo("P-7001");
        assertThat(response.root().productName()).isEqualTo("成品甲");
        assertThat(response.root().depth()).isZero();
        assertThat(response.root().links()).isEmpty();
        assertThat(response.upstream().links()).isEmpty();
        assertThat(response.downstream().links()).isEmpty();
        assertThat(response.limits().truncated()).isFalse();
        assertThat(response.limits().truncationReasons()).isEmpty();
        assertThat(response.limits().scopeLimited()).isFalse();
        assertThat(response.limits().maxDepth()).isEqualTo(5);
        assertThat(response.limits().perLevelNodeLimit()).isEqualTo(200);
        assertThat(response.limits().totalNodeLimit()).isEqualTo(500);
    }

    @Test
    void clampsMaxDepthAtBothEnds() {
        when(transactionMapper.selectList(any())).thenReturn(List.of());

        InventoryLotGenealogyQuery low = query(7001L, "LOT-1");
        low.setMaxDepth(0);
        assertThat(service.genealogy(low).limits().maxDepth()).isEqualTo(1);

        InventoryLotGenealogyQuery high = query(7001L, "LOT-1");
        high.setMaxDepth(99);
        assertThat(service.genealogy(high).limits().maxDepth()).isEqualTo(10);
    }

    @Test
    void honoursDirectionFilter() {
        when(transactionMapper.selectList(any())).thenReturn(List.of());

        InventoryLotGenealogyQuery upstreamOnly = query(7001L, "LOT-1");
        upstreamOnly.setDirection("upstream");
        InventoryLotGenealogyResponse response = service.genealogy(upstreamOnly);

        assertThat(response.upstream()).isNotNull();
        assertThat(response.downstream()).isNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=InventoryLotGenealogyServiceTest`
Expected: compilation failure — the query, response records, and both new services do not exist.

- [ ] **Step 3: Create the DTOs**

`InventoryLotGenealogyQuery.java` — a bean, matching `InventoryLotTraceQuery`'s style (Spring binds query params through setters):

```java
package com.tuowei.erp.inventory.stock.web;

public class InventoryLotGenealogyQuery {

    private Long productId;
    private String lotNo;
    private String direction;
    private Integer maxDepth;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getLotNo() { return lotNo; }
    public void setLotNo(String lotNo) { this.lotNo = lotNo; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public Integer getMaxDepth() { return maxDepth; }
    public void setMaxDepth(Integer maxDepth) { this.maxDepth = maxDepth; }
}
```

The five response records, each in its own file under `inventory/stock/web/`:

```java
public record CounterpartyRef(String type, Long id, String code, String name, String documentNo) {}
```

```java
public record LotGenealogyLink(
        String bizType, String bizNo, String bizLabel, String documentRoute,
        java.time.LocalDateTime occurredTime, java.math.BigDecimal qty,
        Long warehouseId, String warehouseName,
        CounterpartyRef counterparty, String terminalReason, LotGenealogyNode node) {}
```

```java
public record LotGenealogyNode(
        Long productId, String productCode, String productName,
        String lotNo, java.time.LocalDate productionDate, java.time.LocalDate expiryDate,
        int depth, java.util.List<LotGenealogyLink> links) {}
```

```java
public record GenealogyLimits(
        int maxDepth, int perLevelNodeLimit, int totalNodeLimit,
        boolean truncated, java.util.List<String> truncationReasons, boolean scopeLimited) {}
```

```java
public record InventoryLotGenealogyResponse(
        LotGenealogyNode root, LotGenealogyNode upstream, LotGenealogyNode downstream,
        GenealogyLimits limits) {}
```

- [ ] **Step 4: Create `LotGenealogyDisplayResolver`**

```java
package com.tuowei.erp.inventory.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Batched product and warehouse display hydration for the lot genealogy tree. */
@Component
public class LotGenealogyDisplayResolver {

    public record ProductDisplay(String code, String name) {}

    private final ProductMapper productMapper;
    private final WarehouseMapper warehouseMapper;

    public LotGenealogyDisplayResolver(ProductMapper productMapper, WarehouseMapper warehouseMapper) {
        this.productMapper = productMapper;
        this.warehouseMapper = warehouseMapper;
    }

    public Map<Long, ProductDisplay> products(Collection<Long> productIds) {
        Set<Long> ids = ids(productIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return productMapper.selectList(new LambdaQueryWrapper<ProductEntity>()
                        .in(ProductEntity::getId, ids))
                .stream()
                .collect(Collectors.toMap(ProductEntity::getId,
                        entity -> new ProductDisplay(entity.getProductCode(), entity.getProductName()),
                        (left, right) -> left));
    }

    public Map<Long, String> warehouseNames(Collection<Long> warehouseIds) {
        Set<Long> ids = ids(warehouseIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return warehouseMapper.selectList(new LambdaQueryWrapper<WarehouseEntity>()
                        .in(WarehouseEntity::getId, ids))
                .stream()
                .collect(Collectors.toMap(WarehouseEntity::getId,
                        WarehouseEntity::getWarehouseName, (left, right) -> left));
    }

    private static Set<Long> ids(Collection<Long> raw) {
        return raw == null ? Set.of()
                : raw.stream().filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
    }
}
```

- [ ] **Step 5: Create `LotGenealogyCounterpartyResolver` with an empty index**

Only the shape is needed now; Task 3 fills in the resolution.

```java
package com.tuowei.erp.inventory.stock.service;

import com.tuowei.erp.inventory.stock.web.CounterpartyRef;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

/** Batched supplier and customer resolution from purchase receipt and sales delivery numbers. */
@Component
public class LotGenealogyCounterpartyResolver {

    public record CounterpartyIndex(
            Map<String, CounterpartyRef> bySupplierDocument,
            Map<String, CounterpartyRef> byCustomerDocument) {

        public static CounterpartyIndex empty() {
            return new CounterpartyIndex(Map.of(), Map.of());
        }

        public CounterpartyRef supplierFor(String receiptNo) {
            return receiptNo == null ? null : bySupplierDocument.get(receiptNo);
        }

        public CounterpartyRef customerFor(String deliveryNo) {
            return deliveryNo == null ? null : byCustomerDocument.get(deliveryNo);
        }
    }

    public CounterpartyIndex resolve(
            Collection<String> receiptNos, Collection<String> deliveryNos,
            Long companyId, Long accountBookId) {
        return CounterpartyIndex.empty();
    }
}
```

- [ ] **Step 6: Create `InventoryLotGenealogyService` with validation and a bare root**

```java
package com.tuowei.erp.inventory.stock.service;

import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.inventory.stock.web.GenealogyLimits;
import com.tuowei.erp.inventory.stock.web.InventoryLotGenealogyQuery;
import com.tuowei.erp.inventory.stock.web.InventoryLotGenealogyResponse;
import com.tuowei.erp.inventory.stock.web.LotGenealogyNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class InventoryLotGenealogyService {

    static final int DEFAULT_MAX_DEPTH = 5;
    static final int MIN_MAX_DEPTH = 1;
    static final int HARD_MAX_DEPTH = 10;
    static final int PER_LEVEL_NODE_LIMIT = 200;
    static final int TOTAL_NODE_LIMIT = 500;

    private static final String DIRECTION_IN = "IN";
    private static final String DIRECTION_OUT = "OUT";

    private final InventoryTransactionMapper inventoryTransactionMapper;
    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;
    private final InventoryDocumentLinkResolver documentLinkResolver;
    private final LotGenealogyCounterpartyResolver counterpartyResolver;
    private final LotGenealogyDisplayResolver displayResolver;

    public InventoryLotGenealogyService(
            InventoryTransactionMapper inventoryTransactionMapper,
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService,
            InventoryDocumentLinkResolver documentLinkResolver,
            LotGenealogyCounterpartyResolver counterpartyResolver,
            LotGenealogyDisplayResolver displayResolver) {
        this.inventoryTransactionMapper = inventoryTransactionMapper;
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
        this.documentLinkResolver = documentLinkResolver;
        this.counterpartyResolver = counterpartyResolver;
        this.displayResolver = displayResolver;
    }

    @Transactional(readOnly = true)
    public InventoryLotGenealogyResponse genealogy(InventoryLotGenealogyQuery query) {
        InventoryLotGenealogyQuery safeQuery = query == null ? new InventoryLotGenealogyQuery() : query;
        if (safeQuery.getProductId() == null) {
            throw new IllegalArgumentException("批次谱系必须指定商品");
        }
        String lotNo = safeQuery.getLotNo() == null ? null : safeQuery.getLotNo().trim();
        if (!StringUtils.hasText(lotNo)) {
            throw new IllegalArgumentException("批次谱系必须指定批次号");
        }
        Direction direction = Direction.parse(safeQuery.getDirection());
        int maxDepth = clampMaxDepth(safeQuery.getMaxDepth());

        CurrentUser user = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        LotKey rootKey = new LotKey(safeQuery.getProductId(), lotNo);
        List<String> truncationReasons = new ArrayList<>();

        // Task 3 onwards replaces these with real traversals.
        Map<Long, LotGenealogyDisplayResolver.ProductDisplay> products =
                displayResolver.products(Set.of(rootKey.productId()));
        LotGenealogyNode root = bareNode(rootKey, 0, products);

        return new InventoryLotGenealogyResponse(
                root,
                direction.includesUpstream() ? bareNode(rootKey, 0, products) : null,
                direction.includesDownstream() ? bareNode(rootKey, 0, products) : null,
                new GenealogyLimits(maxDepth, PER_LEVEL_NODE_LIMIT, TOTAL_NODE_LIMIT,
                        !truncationReasons.isEmpty(), List.copyOf(new LinkedHashSet<>(truncationReasons)),
                        !snapshot.hasAllScope()));
    }

    private LotGenealogyNode bareNode(
            LotKey key, int depth, Map<Long, LotGenealogyDisplayResolver.ProductDisplay> products) {
        LotGenealogyDisplayResolver.ProductDisplay display = products.get(key.productId());
        return new LotGenealogyNode(
                key.productId(),
                display == null ? null : display.code(),
                display == null ? null : display.name(),
                key.lotNo(), null, null, depth, List.of());
    }

    private static int clampMaxDepth(Integer requested) {
        if (requested == null) {
            return DEFAULT_MAX_DEPTH;
        }
        return Math.max(MIN_MAX_DEPTH, Math.min(HARD_MAX_DEPTH, requested));
    }

    record LotKey(Long productId, String lotNo) {}

    enum Direction {
        UPSTREAM, DOWNSTREAM, BOTH;

        static Direction parse(String raw) {
            if (!StringUtils.hasText(raw)) {
                return BOTH;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("批次谱系方向只支持 UPSTREAM、DOWNSTREAM 或 BOTH");
            }
        }

        boolean includesUpstream() { return this != DOWNSTREAM; }
        boolean includesDownstream() { return this != UPSTREAM; }
    }
}
```

Check `CurrentUserContext` for the accessor that returns `CurrentUser`. `InventoryLotQueryService` has a private `currentUser()` helper — mirror whatever it calls (the test above assumes `requireCurrentUser()`; if the real name differs, use the real one and update the test's `when(...)` to match).

- [ ] **Step 7: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=InventoryLotGenealogyServiceTest`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/tuowei/erp/inventory/stock/web/ \
        backend/src/main/java/com/tuowei/erp/inventory/stock/service/ \
        backend/src/test/java/com/tuowei/erp/inventory/stock/InventoryLotGenealogyServiceTest.java
git commit -m "feat: add lot genealogy contract and validation"
```

---

## Task 3: Upstream level one — purchase receipt terminal and supplier resolution

**Files:**
- Modify: `service/InventoryLotGenealogyService.java`, `service/LotGenealogyCounterpartyResolver.java`
- Test: `InventoryLotGenealogyServiceTest.java`, new `LotGenealogyCounterpartyResolverTest.java`

**Interfaces:**
- Produces: `LotGenealogyCounterpartyResolver.resolve(Collection<String> receiptNos, Collection<String> deliveryNos, Long companyId, Long accountBookId) → CounterpartyIndex`, populated. `InventoryLotGenealogyService` gains private `traverse(LotKey root, Direction, int maxDepth, Accumulator)`.

- [ ] **Step 1: Write the failing tests**

Add to `InventoryLotGenealogyServiceTest`:

```java
    @Test
    void upstreamResolvesSupplierAndTerminatesAtPurchaseReceipt() {
        when(transactionMapper.selectList(any()))
                .thenReturn(List.of(txn(7001L, "LOT-1", "PURCHASE_RECEIPT", "PR-1", "IN")));
        when(counterpartyResolver.resolve(any(), any(), any(), any())).thenReturn(
                new LotGenealogyCounterpartyResolver.CounterpartyIndex(
                        java.util.Map.of("PR-1", new com.tuowei.erp.inventory.stock.web.CounterpartyRef(
                                "SUPPLIER", 501L, "S-501", "上游供应商", "PO-1")),
                        java.util.Map.of()));

        InventoryLotGenealogyQuery request = query(7001L, "LOT-1");
        request.setDirection("UPSTREAM");
        var upstream = service.genealogy(request).upstream();

        assertThat(upstream.links()).hasSize(1);
        var link = upstream.links().get(0);
        assertThat(link.bizType()).isEqualTo("PURCHASE_RECEIPT");
        assertThat(link.bizNo()).isEqualTo("PR-1");
        assertThat(link.bizLabel()).isEqualTo("采购收货");
        assertThat(link.documentRoute()).isEqualTo("/purchase/receipts?keyword=PR-1");
        assertThat(link.warehouseName()).isEqualTo("主仓");
        assertThat(link.qty()).isEqualByComparingTo("10");
        assertThat(link.terminalReason()).isEqualTo("PURCHASED");
        assertThat(link.node()).isNull();
        assertThat(link.counterparty().type()).isEqualTo("SUPPLIER");
        assertThat(link.counterparty().name()).isEqualTo("上游供应商");
        assertThat(link.counterparty().documentNo()).isEqualTo("PO-1");
    }

    @Test
    void upstreamFillsRootLotDatesFromTransactions() {
        when(transactionMapper.selectList(any()))
                .thenReturn(List.of(txn(7001L, "LOT-1", "PURCHASE_RECEIPT", "PR-1", "IN")));

        InventoryLotGenealogyQuery request = query(7001L, "LOT-1");
        request.setDirection("UPSTREAM");
        var upstream = service.genealogy(request).upstream();

        assertThat(upstream.productionDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(upstream.expiryDate()).isEqualTo(LocalDate.of(2027, 7, 1));
    }

    @Test
    void upstreamQueriesOnlyInboundRowsForTheTenant() {
        when(transactionMapper.selectList(any())).thenReturn(List.of());

        InventoryLotGenealogyQuery request = query(7001L, "LOT-1");
        request.setDirection("UPSTREAM");
        service.genealogy(request);

        org.mockito.ArgumentCaptor<LambdaQueryWrapper<InventoryTransactionEntity>> captor =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        org.mockito.Mockito.verify(transactionMapper).selectList(captor.capture());
        String sql = captor.getValue().getTargetSql();
        assertThat(sql).contains("COMPANY_ID", "ACCOUNT_BOOK_ID", "DIRECTION");
        assertThat(captor.getValue().getParamNameValuePairs().values())
                .contains(USER.companyId(), USER.accountBookId(), "IN");
    }
```

New `LotGenealogyCounterpartyResolverTest.java`:

```java
package com.tuowei.erp.inventory.stock;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.inventory.stock.service.LotGenealogyCounterpartyResolver;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LotGenealogyCounterpartyResolverTest {

    @Mock private PurchaseReceiptMapper receiptMapper;
    @Mock private PurchaseOrderMapper purchaseOrderMapper;
    @Mock private SupplierMapper supplierMapper;
    @Mock private SalesDeliveryMapper deliveryMapper;
    @Mock private SalesOrderMapper salesOrderMapper;
    @Mock private CustomerMapper customerMapper;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, PurchaseReceiptEntity.class);
        TableInfoHelper.initTableInfo(assistant, PurchaseOrderEntity.class);
        TableInfoHelper.initTableInfo(assistant, SupplierEntity.class);
    }

    private LotGenealogyCounterpartyResolver resolver() {
        return new LotGenealogyCounterpartyResolver(receiptMapper, purchaseOrderMapper, supplierMapper,
                deliveryMapper, salesOrderMapper, customerMapper);
    }

    @Test
    void resolvesSupplierThroughReceiptAndOrderInThreeBatchedQueries() {
        PurchaseReceiptEntity receipt = new PurchaseReceiptEntity();
        receipt.setId(1L);
        receipt.setReceiptNo("PR-1");
        receipt.setOrderId(31L);
        PurchaseOrderEntity order = new PurchaseOrderEntity();
        order.setId(31L);
        order.setOrderNo("PO-1");
        order.setSupplierId(501L);
        SupplierEntity supplier = new SupplierEntity();
        supplier.setId(501L);
        supplier.setSupplierCode("S-501");
        supplier.setSupplierName("上游供应商");

        when(receiptMapper.selectList(any())).thenReturn(List.of(receipt));
        when(purchaseOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(supplierMapper.selectList(any())).thenReturn(List.of(supplier));

        var index = resolver().resolve(Set.of("PR-1"), Set.of(), 101L, 202L);

        var ref = index.supplierFor("PR-1");
        assertThat(ref.type()).isEqualTo("SUPPLIER");
        assertThat(ref.id()).isEqualTo(501L);
        assertThat(ref.code()).isEqualTo("S-501");
        assertThat(ref.name()).isEqualTo("上游供应商");
        assertThat(ref.documentNo()).isEqualTo("PO-1");
        org.mockito.Mockito.verify(receiptMapper).selectList(any());
        org.mockito.Mockito.verify(purchaseOrderMapper).selectList(any());
        org.mockito.Mockito.verify(supplierMapper).selectList(any());
    }

    @Test
    void skipsQueriesEntirelyWhenNoDocumentNumbersAreRequested() {
        var index = resolver().resolve(Set.of(), Set.of(), 101L, 202L);

        assertThat(index.supplierFor("PR-1")).isNull();
        assertThat(index.customerFor("SD-1")).isNull();
        verifyNoInteractions(receiptMapper, purchaseOrderMapper, supplierMapper,
                deliveryMapper, salesOrderMapper, customerMapper);
    }

    @Test
    void degradesToNullWhenTheChainIsBroken() {
        PurchaseReceiptEntity receipt = new PurchaseReceiptEntity();
        receipt.setReceiptNo("PR-1");
        receipt.setOrderId(null);
        when(receiptMapper.selectList(any())).thenReturn(List.of(receipt));

        assertThat(resolver().resolve(Set.of("PR-1"), Set.of(), 101L, 202L).supplierFor("PR-1")).isNull();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest='InventoryLotGenealogyServiceTest+LotGenealogyCounterpartyResolverTest'`
Expected: FAIL — the resolver has no six-arg constructor and the traversal returns no links.

- [ ] **Step 3: Implement `LotGenealogyCounterpartyResolver`**

Replace the stub body. Both directions follow the same three-batch shape; only the supplier half is shown — write the customer half identically with `SalesDeliveryEntity::getDeliveryNo` → `SalesOrderEntity` → `CustomerEntity`, `type = "CUSTOMER"`, and `documentNo = salesOrder.getOrderNo()`.

```java
    private final PurchaseReceiptMapper receiptMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final SupplierMapper supplierMapper;
    private final SalesDeliveryMapper deliveryMapper;
    private final SalesOrderMapper salesOrderMapper;
    private final CustomerMapper customerMapper;

    // constructor assigns all six

    public CounterpartyIndex resolve(
            Collection<String> receiptNos, Collection<String> deliveryNos,
            Long companyId, Long accountBookId) {
        return new CounterpartyIndex(
                resolveSuppliers(texts(receiptNos), companyId, accountBookId),
                resolveCustomers(texts(deliveryNos), companyId, accountBookId));
    }

    private Map<String, CounterpartyRef> resolveSuppliers(
            Set<String> receiptNos, Long companyId, Long accountBookId) {
        if (receiptNos.isEmpty()) {
            return Map.of();
        }
        List<PurchaseReceiptEntity> receipts = receiptMapper.selectList(
                new LambdaQueryWrapper<PurchaseReceiptEntity>()
                        .eq(PurchaseReceiptEntity::getCompanyId, companyId)
                        .eq(PurchaseReceiptEntity::getAccountBookId, accountBookId)
                        .in(PurchaseReceiptEntity::getReceiptNo, receiptNos));
        Set<Long> orderIds = receipts.stream()
                .map(PurchaseReceiptEntity::getOrderId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, PurchaseOrderEntity> orders = purchaseOrderMapper.selectList(
                        new LambdaQueryWrapper<PurchaseOrderEntity>()
                                .in(PurchaseOrderEntity::getId, orderIds))
                .stream()
                .collect(Collectors.toMap(PurchaseOrderEntity::getId, order -> order, (l, r) -> l));
        Set<Long> supplierIds = orders.values().stream()
                .map(PurchaseOrderEntity::getSupplierId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, SupplierEntity> suppliers = supplierIds.isEmpty() ? Map.of()
                : supplierMapper.selectList(new LambdaQueryWrapper<SupplierEntity>()
                                .in(SupplierEntity::getId, supplierIds))
                        .stream()
                        .collect(Collectors.toMap(SupplierEntity::getId, s -> s, (l, r) -> l));

        Map<String, CounterpartyRef> resolved = new HashMap<>();
        for (PurchaseReceiptEntity receipt : receipts) {
            PurchaseOrderEntity order = receipt.getOrderId() == null ? null : orders.get(receipt.getOrderId());
            if (order == null) {
                continue;
            }
            SupplierEntity supplier = order.getSupplierId() == null ? null : suppliers.get(order.getSupplierId());
            if (supplier == null) {
                continue;
            }
            resolved.put(receipt.getReceiptNo(), new CounterpartyRef(
                    "SUPPLIER", supplier.getId(), supplier.getSupplierCode(),
                    supplier.getSupplierName(), order.getOrderNo()));
        }
        return Map.copyOf(resolved);
    }

    private static Set<String> texts(Collection<String> raw) {
        return raw == null ? Set.of()
                : raw.stream().filter(StringUtils::hasText).collect(Collectors.toSet());
    }
```

- [ ] **Step 4: Implement the level-batched traversal skeleton**

Add to `InventoryLotGenealogyService`. Mutable builders are needed because records are immutable and the walk is top-down; a final conversion produces the record tree.

```java
    private static final class NodeBuilder {
        private final LotKey key;
        private final int depth;
        private final List<LinkBuilder> links = new ArrayList<>();

        private NodeBuilder(LotKey key, int depth) {
            this.key = key;
            this.depth = depth;
        }
    }

    private static final class LinkBuilder {
        private InventoryTransactionEntity txn;
        private String terminalReason;
        private NodeBuilder child;
    }

    /** Accumulates lot dates seen anywhere in the walk, plus the guard reasons that fired. */
    private static final class Accumulator {
        private final Map<LotKey, InventoryTransactionEntity> lotDates = new HashMap<>();
        private final Set<Long> productIds = new LinkedHashSet<>();
        private final Set<Long> warehouseIds = new LinkedHashSet<>();
        private final Set<String> receiptNos = new LinkedHashSet<>();
        private final Set<String> deliveryNos = new LinkedHashSet<>();
        private final Set<String> truncationReasons = new LinkedHashSet<>();

        private void observe(InventoryTransactionEntity txn) {
            LotKey key = new LotKey(txn.getProductId(), txn.getLotNo());
            lotDates.putIfAbsent(key, txn);
            if (txn.getProductId() != null) productIds.add(txn.getProductId());
            if (txn.getWarehouseId() != null) warehouseIds.add(txn.getWarehouseId());
            if ("PURCHASE_RECEIPT".equals(txn.getBizType()) && StringUtils.hasText(txn.getBizNo())) {
                receiptNos.add(txn.getBizNo());
            }
            if ("SALES_DELIVERY".equals(txn.getBizType()) && StringUtils.hasText(txn.getBizNo())) {
                deliveryNos.add(txn.getBizNo());
            }
        }
    }

    private NodeBuilder traverse(
            LotKey rootKey, Direction walk, int maxDepth, CurrentUser user,
            DataScopeSnapshot snapshot, Accumulator accumulator) {
        String txnDirection = walk == Direction.UPSTREAM ? DIRECTION_IN : DIRECTION_OUT;
        NodeBuilder root = new NodeBuilder(rootKey, 0);
        accumulator.productIds.add(rootKey.productId());
        List<NodeBuilder> frontier = new ArrayList<>(List.of(root));
        Set<LotKey> visited = new HashSet<>(Set.of(rootKey));
        int depth = 0;

        while (!frontier.isEmpty() && depth < maxDepth) {
            List<LotKey> keys = frontier.stream().map(node -> node.key).toList();
            List<InventoryTransactionEntity> rows =
                    loadLevelTransactions(keys, txnDirection, user, snapshot);
            rows.forEach(accumulator::observe);
            Map<LotKey, List<InventoryTransactionEntity>> byLot = rows.stream()
                    .collect(Collectors.groupingBy(row -> new LotKey(row.getProductId(), row.getLotNo())));

            List<NodeBuilder> next = new ArrayList<>();
            for (NodeBuilder node : frontier) {
                for (InventoryTransactionEntity row : byLot.getOrDefault(node.key, List.of())) {
                    LinkBuilder link = new LinkBuilder();
                    link.txn = row;
                    link.terminalReason = terminalReason(walk, row.getBizType());
                    node.links.add(link);
                }
            }
            frontier = next;
            depth++;
        }
        return root;
    }

    private List<InventoryTransactionEntity> loadLevelTransactions(
            List<LotKey> keys, String txnDirection, CurrentUser user, DataScopeSnapshot snapshot) {
        LambdaQueryWrapper<InventoryTransactionEntity> wrapper =
                new LambdaQueryWrapper<InventoryTransactionEntity>()
                        .eq(InventoryTransactionEntity::getCompanyId, user.companyId())
                        .eq(InventoryTransactionEntity::getAccountBookId, user.accountBookId())
                        .eq(InventoryTransactionEntity::getDirection, txnDirection)
                        .and(outer -> {
                            for (LotKey key : keys) {
                                outer.or(inner -> inner
                                        .eq(InventoryTransactionEntity::getProductId, key.productId())
                                        .eq(InventoryTransactionEntity::getLotNo, key.lotNo()));
                            }
                        })
                        .orderByAsc(InventoryTransactionEntity::getOccurredTime)
                        .orderByAsc(InventoryTransactionEntity::getId);
        return inventoryTransactionMapper.selectList(
                dataScopeService.applyInventoryTransactionScope(wrapper, snapshot));
    }

    /** Upstream terminal reasons; Task 5 adds the downstream arm and Task 6 the rest. */
    private String terminalReason(Direction walk, String bizType) {
        String type = bizType == null ? "" : bizType.trim().toUpperCase(Locale.ROOT);
        if (walk == Direction.UPSTREAM) {
            return switch (type) {
                case "PURCHASE_RECEIPT" -> "PURCHASED";
                default -> "UNKNOWN_SOURCE";
            };
        }
        return "UNKNOWN_DESTINATION";
    }
```

- [ ] **Step 5: Convert builders to records and wire `genealogy` to the traversal**

```java
    private LotGenealogyNode toNode(
            NodeBuilder builder, Accumulator accumulator,
            Map<Long, LotGenealogyDisplayResolver.ProductDisplay> products,
            Map<Long, String> warehouseNames,
            LotGenealogyCounterpartyResolver.CounterpartyIndex counterparties) {
        LotGenealogyDisplayResolver.ProductDisplay display = products.get(builder.key.productId());
        InventoryTransactionEntity dates = accumulator.lotDates.get(builder.key);
        List<LotGenealogyLink> links = builder.links.stream()
                .map(link -> toLink(link, accumulator, products, warehouseNames, counterparties))
                .toList();
        return new LotGenealogyNode(
                builder.key.productId(),
                display == null ? null : display.code(),
                display == null ? null : display.name(),
                builder.key.lotNo(),
                dates == null ? null : dates.getProductionDate(),
                dates == null ? null : dates.getExpiryDate(),
                builder.depth,
                links);
    }

    private LotGenealogyLink toLink(
            LinkBuilder builder, Accumulator accumulator,
            Map<Long, LotGenealogyDisplayResolver.ProductDisplay> products,
            Map<Long, String> warehouseNames,
            LotGenealogyCounterpartyResolver.CounterpartyIndex counterparties) {
        InventoryTransactionEntity txn = builder.txn;
        String bizType = txn.getBizType();
        CounterpartyRef counterparty = switch (bizType == null ? "" : bizType) {
            case "PURCHASE_RECEIPT", "PURCHASE_RETURN" -> counterparties.supplierFor(txn.getBizNo());
            case "SALES_DELIVERY", "SALES_RETURN" -> counterparties.customerFor(txn.getBizNo());
            default -> null;
        };
        return new LotGenealogyLink(
                bizType,
                txn.getBizNo(),
                documentLinkResolver.resolveLabel(bizType),
                documentLinkResolver.resolveRoute(bizType, txn.getBizNo()),
                txn.getOccurredTime(),
                ScalePrecision.quantity(ScalePrecision.zeroDefault(txn.getQty())),
                txn.getWarehouseId(),
                txn.getWarehouseId() == null ? null : warehouseNames.get(txn.getWarehouseId()),
                counterparty,
                builder.terminalReason,
                builder.child == null ? null
                        : toNode(builder.child, accumulator, products, warehouseNames, counterparties));
    }
```

In `genealogy`, replace the placeholder body: build one `Accumulator`, run `traverse` for each requested direction, then resolve display and counterparty batches **once** from the accumulator and convert both trees. Note the ordering — hydration must run after both traversals so it batches across them.

```java
        Accumulator accumulator = new Accumulator();
        NodeBuilder upstream = direction.includesUpstream()
                ? traverse(rootKey, Direction.UPSTREAM, maxDepth, user, snapshot, accumulator) : null;
        NodeBuilder downstream = direction.includesDownstream()
                ? traverse(rootKey, Direction.DOWNSTREAM, maxDepth, user, snapshot, accumulator) : null;

        Map<Long, LotGenealogyDisplayResolver.ProductDisplay> products =
                displayResolver.products(accumulator.productIds);
        Map<Long, String> warehouseNames = displayResolver.warehouseNames(accumulator.warehouseIds);
        LotGenealogyCounterpartyResolver.CounterpartyIndex counterparties = counterpartyResolver.resolve(
                accumulator.receiptNos, accumulator.deliveryNos, user.companyId(), user.accountBookId());

        LotGenealogyNode root = bareNode(rootKey, 0, products);
        return new InventoryLotGenealogyResponse(
                root,
                upstream == null ? null : toNode(upstream, accumulator, products, warehouseNames, counterparties),
                downstream == null ? null : toNode(downstream, accumulator, products, warehouseNames, counterparties),
                new GenealogyLimits(maxDepth, PER_LEVEL_NODE_LIMIT, TOTAL_NODE_LIMIT,
                        !accumulator.truncationReasons.isEmpty(),
                        List.copyOf(accumulator.truncationReasons),
                        !snapshot.hasAllScope()));
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest='InventoryLotGenealogyServiceTest+LotGenealogyCounterpartyResolverTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/tuowei/erp/inventory/stock/service/ \
        backend/src/test/java/com/tuowei/erp/inventory/stock/
git commit -m "feat: traverse lot genealogy upstream to supplier"
```

---

## Task 4: Upstream recursion across the production order

The heart of the feature: crossing the manufacturing boundary.

**Files:**
- Modify: `service/InventoryLotGenealogyService.java`
- Test: `InventoryLotGenealogyServiceTest.java`

**Interfaces:**
- Produces: private `loadProductionCounterparts(Collection<String> orderNos, String counterBizType, String counterDirection, CurrentUser, DataScopeSnapshot) → List<InventoryTransactionEntity>`.

- [ ] **Step 1: Write the failing tests**

```java
    @Test
    void upstreamExpandsProductionCompletionIntoConsumedMaterialLots() {
        when(transactionMapper.selectList(any()))
                // level 0: the finished lot was produced by MO-1
                .thenReturn(List.of(txn(7001L, "LOT-F", "PRODUCTION_COMPLETION", "MO-1", "IN")))
                // MO-1 consumed two material lots
                .thenReturn(List.of(
                        txn(8001L, "LOT-A", "PRODUCTION_ISSUE", "MO-1", "OUT"),
                        txn(8002L, "LOT-B", "PRODUCTION_ISSUE", "MO-1", "OUT")))
                // level 1: both material lots were purchased
                .thenReturn(List.of(
                        txn(8001L, "LOT-A", "PURCHASE_RECEIPT", "PR-A", "IN"),
                        txn(8002L, "LOT-B", "PURCHASE_RECEIPT", "PR-B", "IN")))
                .thenReturn(List.of());

        InventoryLotGenealogyQuery request = query(7001L, "LOT-F");
        request.setDirection("UPSTREAM");
        var upstream = service.genealogy(request).upstream();

        assertThat(upstream.links()).hasSize(1);
        var completion = upstream.links().get(0);
        assertThat(completion.bizType()).isEqualTo("PRODUCTION_COMPLETION");
        assertThat(completion.terminalReason()).isNull();
        assertThat(completion.node()).isNotNull();

        // One child node per distinct consumed material lot, reached through MO-1.
        var materials = completion.node().links();
        assertThat(materials).hasSize(2);
        assertThat(materials).allSatisfy(link -> assertThat(link.bizType()).isEqualTo("PRODUCTION_ISSUE"));
        assertThat(materials).extracting(link -> link.node().lotNo())
                .containsExactlyInAnyOrder("LOT-A", "LOT-B");
        assertThat(materials).extracting(link -> link.node().depth()).containsOnly(1);

        // And each material lot terminates at its own purchase receipt at depth 2.
        var purchased = materials.stream()
                .flatMap(link -> link.node().links().stream())
                .toList();
        assertThat(purchased).hasSize(2);
        assertThat(purchased).extracting(com.tuowei.erp.inventory.stock.web.LotGenealogyLink::terminalReason)
                .containsOnly("PURCHASED");
    }

    @Test
    void upstreamMarksProductionCompletionWithNoIssuedMaterial() {
        when(transactionMapper.selectList(any()))
                .thenReturn(List.of(txn(7001L, "LOT-F", "PRODUCTION_COMPLETION", "MO-9", "IN")))
                .thenReturn(List.of());

        InventoryLotGenealogyQuery request = query(7001L, "LOT-F");
        request.setDirection("UPSTREAM");
        var link = service.genealogy(request).upstream().links().get(0);

        assertThat(link.terminalReason()).isEqualTo("NO_MATERIAL_ISSUED");
        assertThat(link.node()).isNull();
    }

    @Test
    void upstreamExpandsEachLevelInOneBatchedQuery() {
        when(transactionMapper.selectList(any()))
                .thenReturn(List.of(txn(7001L, "LOT-F", "PRODUCTION_COMPLETION", "MO-1", "IN")))
                .thenReturn(List.of(
                        txn(8001L, "LOT-A", "PRODUCTION_ISSUE", "MO-1", "OUT"),
                        txn(8002L, "LOT-B", "PRODUCTION_ISSUE", "MO-1", "OUT")))
                .thenReturn(List.of())
                .thenReturn(List.of());

        InventoryLotGenealogyQuery request = query(7001L, "LOT-F");
        request.setDirection("UPSTREAM");
        service.genealogy(request);

        // Level 0 rows, MO-1 counterparts, then level 1 rows for BOTH material lots in a single
        // query. Four or fewer calls proves the walk is level-batched, not one query per node.
        org.mockito.Mockito.verify(transactionMapper, org.mockito.Mockito.times(3)).selectList(any());
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest=InventoryLotGenealogyServiceTest`
Expected: FAIL — `PRODUCTION_COMPLETION` currently yields `UNKNOWN_SOURCE` and never expands.

- [ ] **Step 3: Add expansion to the traversal loop**

Inside `traverse`, replace the inner per-node loop with a two-phase level: first classify links and collect the production order numbers that need expanding, then issue one counter-side query and attach children.

```java
            boolean expandsThroughProduction = depth + 1 < maxDepth || true; // links are built regardless
            String expandBizType = walk == Direction.UPSTREAM ? "PRODUCTION_COMPLETION" : "PRODUCTION_ISSUE";
            String counterBizType = walk == Direction.UPSTREAM ? "PRODUCTION_ISSUE" : "PRODUCTION_COMPLETION";
            String counterDirection = walk == Direction.UPSTREAM ? DIRECTION_OUT : DIRECTION_IN;

            // Phase 1: build links, collecting production order numbers to expand.
            List<LinkBuilder> expandable = new ArrayList<>();
            Set<String> orderNos = new LinkedHashSet<>();
            for (NodeBuilder node : frontier) {
                for (InventoryTransactionEntity row : byLot.getOrDefault(node.key, List.of())) {
                    LinkBuilder link = new LinkBuilder();
                    link.txn = row;
                    node.links.add(link);
                    if (expandBizType.equalsIgnoreCase(row.getBizType()) && StringUtils.hasText(row.getBizNo())) {
                        expandable.add(link);
                        orderNos.add(row.getBizNo().trim());
                    } else {
                        link.terminalReason = terminalReason(walk, row.getBizType());
                    }
                }
            }

            // Phase 2: one batched counter-side query for every production order at this level.
            Map<String, List<InventoryTransactionEntity>> counterparts = orderNos.isEmpty()
                    ? Map.of()
                    : loadProductionCounterparts(orderNos, counterBizType, counterDirection, user, snapshot)
                            .stream()
                            .collect(Collectors.groupingBy(row -> row.getBizNo().trim()));
            counterparts.values().forEach(rows2 -> rows2.forEach(accumulator::observe));

            for (LinkBuilder link : expandable) {
                List<InventoryTransactionEntity> children =
                        counterparts.getOrDefault(link.txn.getBizNo().trim(), List.of());
                if (children.isEmpty()) {
                    link.terminalReason = walk == Direction.UPSTREAM ? "NO_MATERIAL_ISSUED" : "IN_PRODUCTION";
                    continue;
                }
                // The production order is the edge; each distinct child lot becomes one node.
                // A production order legitimately consumes many materials, so fan out per lot.
                LotKey firstKey = new LotKey(children.get(0).getProductId(), children.get(0).getLotNo());
                NodeBuilder orderNode = new NodeBuilder(link.txn == null ? firstKey : link.child == null
                        ? new LotKey(link.txn.getProductId(), link.txn.getLotNo()) : firstKey, depth);
                // Attach one grandchild link per distinct child lot.
                Set<LotKey> seenChildren = new LinkedHashSet<>();
                for (InventoryTransactionEntity child : children) {
                    LotKey childKey = new LotKey(child.getProductId(), child.getLotNo());
                    if (!seenChildren.add(childKey)) {
                        continue;
                    }
                    LinkBuilder childLink = new LinkBuilder();
                    childLink.txn = child;
                    NodeBuilder childNode = new NodeBuilder(childKey, depth + 1);
                    childLink.child = childNode;
                    orderNode.links.add(childLink);
                    if (visited.add(childKey)) {
                        next.add(childNode);
                    } else {
                        childLink.terminalReason = "ALREADY_VISITED";
                    }
                }
                link.child = orderNode;
            }
```

Note on shape: the `PRODUCTION_COMPLETION` link's `node` is an intermediate node standing for the production order, whose links are the `PRODUCTION_ISSUE` movements into the material lots. The intermediate node reuses the parent lot's key so the tree stays a uniform node/link alternation, which is what the frontend `el-tree` mapper in Task 12 expects. Simplify the `orderNode` construction to `new NodeBuilder(node.key, depth)` — capture the owning `node` when building `expandable` (store it on `LinkBuilder` as a `parentKey` field) rather than the tangled ternary above.

Add the counter-side loader:

```java
    private List<InventoryTransactionEntity> loadProductionCounterparts(
            Collection<String> orderNos, String counterBizType, String counterDirection,
            CurrentUser user, DataScopeSnapshot snapshot) {
        LambdaQueryWrapper<InventoryTransactionEntity> wrapper =
                new LambdaQueryWrapper<InventoryTransactionEntity>()
                        .eq(InventoryTransactionEntity::getCompanyId, user.companyId())
                        .eq(InventoryTransactionEntity::getAccountBookId, user.accountBookId())
                        .eq(InventoryTransactionEntity::getBizType, counterBizType)
                        .eq(InventoryTransactionEntity::getDirection, counterDirection)
                        .in(InventoryTransactionEntity::getBizNo, orderNos)
                        .orderByAsc(InventoryTransactionEntity::getId);
        return inventoryTransactionMapper.selectList(
                dataScopeService.applyInventoryTransactionScope(wrapper, snapshot));
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=InventoryLotGenealogyServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/tuowei/erp/inventory/stock/service/InventoryLotGenealogyService.java \
        backend/src/test/java/com/tuowei/erp/inventory/stock/InventoryLotGenealogyServiceTest.java
git commit -m "feat: recurse lot genealogy across production orders"
```

---

## Task 5: Downstream traversal

**Files:**
- Modify: `service/InventoryLotGenealogyService.java`
- Test: `InventoryLotGenealogyServiceTest.java`

- [ ] **Step 1: Write the failing tests**

```java
    @Test
    void downstreamResolvesCustomerAndTerminatesAtSalesDelivery() {
        when(transactionMapper.selectList(any()))
                .thenReturn(List.of(txn(7001L, "LOT-1", "SALES_DELIVERY", "SD-1", "OUT")))
                .thenReturn(List.of());
        when(counterpartyResolver.resolve(any(), any(), any(), any())).thenReturn(
                new LotGenealogyCounterpartyResolver.CounterpartyIndex(
                        java.util.Map.of(),
                        java.util.Map.of("SD-1", new com.tuowei.erp.inventory.stock.web.CounterpartyRef(
                                "CUSTOMER", 601L, "C-601", "下游客户", "SO-1"))));

        InventoryLotGenealogyQuery request = query(7001L, "LOT-1");
        request.setDirection("DOWNSTREAM");
        var link = service.genealogy(request).downstream().links().get(0);

        assertThat(link.terminalReason()).isEqualTo("SOLD");
        assertThat(link.counterparty().type()).isEqualTo("CUSTOMER");
        assertThat(link.counterparty().name()).isEqualTo("下游客户");
        assertThat(link.counterparty().documentNo()).isEqualTo("SO-1");
    }

    @Test
    void downstreamExpandsProductionIssueIntoProducedFinishedLots() {
        when(transactionMapper.selectList(any()))
                .thenReturn(List.of(txn(8001L, "LOT-A", "PRODUCTION_ISSUE", "MO-1", "OUT")))
                .thenReturn(List.of(txn(7001L, "LOT-F", "PRODUCTION_COMPLETION", "MO-1", "IN")))
                .thenReturn(List.of(txn(7001L, "LOT-F", "SALES_DELIVERY", "SD-1", "OUT")))
                .thenReturn(List.of());

        InventoryLotGenealogyQuery request = query(8001L, "LOT-A");
        request.setDirection("DOWNSTREAM");
        var issue = service.genealogy(request).downstream().links().get(0);

        assertThat(issue.terminalReason()).isNull();
        var produced = issue.node().links();
        assertThat(produced).hasSize(1);
        assertThat(produced.get(0).node().lotNo()).isEqualTo("LOT-F");
        assertThat(produced.get(0).node().links().get(0).terminalReason()).isEqualTo("SOLD");
    }

    @Test
    void downstreamReportsMaterialStillOnTheShopFloor() {
        when(transactionMapper.selectList(any()))
                .thenReturn(List.of(txn(8001L, "LOT-A", "PRODUCTION_ISSUE", "MO-2", "OUT")))
                .thenReturn(List.of());

        InventoryLotGenealogyQuery request = query(8001L, "LOT-A");
        request.setDirection("DOWNSTREAM");
        var link = service.genealogy(request).downstream().links().get(0);

        assertThat(link.terminalReason()).isEqualTo("IN_PRODUCTION");
        assertThat(link.node()).isNull();
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest=InventoryLotGenealogyServiceTest`
Expected: FAIL — `SALES_DELIVERY` yields `UNKNOWN_DESTINATION`.

- [ ] **Step 3: Add the downstream arm to `terminalReason`**

```java
        return switch (type) {
            case "SALES_DELIVERY" -> "SOLD";
            default -> "UNKNOWN_DESTINATION";
        };
```

The `PRODUCTION_ISSUE` expansion and `IN_PRODUCTION` fallback already work — Task 4's loop is direction-parameterised, so only the reason map needs the new arm.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=InventoryLotGenealogyServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/tuowei/erp/inventory/stock/service/InventoryLotGenealogyService.java \
        backend/src/test/java/com/tuowei/erp/inventory/stock/InventoryLotGenealogyServiceTest.java
git commit -m "feat: traverse lot genealogy downstream to customer"
```

---

## Task 6: Every remaining terminal reason, and lots that do not exist

**Files:**
- Modify: `service/InventoryLotGenealogyService.java`
- Test: `InventoryLotGenealogyServiceTest.java`

- [ ] **Step 1: Write the failing tests**

```java
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "SALES_RETURN,RETURNED_BY_CUSTOMER",
            "PRODUCTION_RETURN,MOVED_INTERNALLY",
            "INVENTORY_TRANSFER,MOVED_INTERNALLY",
            "INVENTORY_ADJUSTMENT,ADJUSTED",
            "INVENTORY_CHECK,ADJUSTED",
            "OPENING_BALANCE,OPENING_BALANCE",
            "OPENING_INVENTORY,OPENING_BALANCE",
            "SOMETHING_NEW,UNKNOWN_SOURCE"
    })
    void mapsUpstreamTerminalReasons(String bizType, String expectedReason) {
        when(transactionMapper.selectList(any()))
                .thenReturn(List.of(txn(7001L, "LOT-1", bizType, "DOC-1", "IN")));

        InventoryLotGenealogyQuery request = query(7001L, "LOT-1");
        request.setDirection("UPSTREAM");
        var link = service.genealogy(request).upstream().links().get(0);

        assertThat(link.terminalReason()).isEqualTo(expectedReason);
        // An unknown biz_type must still carry its raw value rather than being dropped.
        assertThat(link.bizType()).isEqualTo(bizType);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "PURCHASE_RETURN,RETURNED_TO_SUPPLIER",
            "PRODUCTION_COMPLETION_REVERSAL,REVERSED",
            "INVENTORY_TRANSFER,MOVED_INTERNALLY",
            "INVENTORY_ADJUSTMENT,ADJUSTED",
            "INVENTORY_CHECK,ADJUSTED",
            "SOMETHING_NEW,UNKNOWN_DESTINATION"
    })
    void mapsDownstreamTerminalReasons(String bizType, String expectedReason) {
        when(transactionMapper.selectList(any()))
                .thenReturn(List.of(txn(7001L, "LOT-1", bizType, "DOC-1", "OUT")))
                .thenReturn(List.of());

        InventoryLotGenealogyQuery request = query(7001L, "LOT-1");
        request.setDirection("DOWNSTREAM");
        var link = service.genealogy(request).downstream().links().get(0);

        assertThat(link.terminalReason()).isEqualTo(expectedReason);
    }

    @Test
    void namesNonLotControlledMaterialInsteadOfDroppingTheBranch() {
        InventoryTransactionEntity nonLotMaterial = txn(8003L, null, "PRODUCTION_ISSUE", "MO-1", "OUT");
        when(transactionMapper.selectList(any()))
                .thenReturn(List.of(txn(7001L, "LOT-F", "PRODUCTION_COMPLETION", "MO-1", "IN")))
                .thenReturn(List.of(nonLotMaterial))
                .thenReturn(List.of());

        InventoryLotGenealogyQuery request = query(7001L, "LOT-F");
        request.setDirection("UPSTREAM");
        var materials = service.genealogy(request).upstream().links().get(0).node().links();

        assertThat(materials).hasSize(1);
        assertThat(materials.get(0).terminalReason()).isEqualTo("MATERIAL_NOT_LOT_CONTROLLED");
        // The consumed product is still named, so the recall does not silently lose it.
        assertThat(materials.get(0).node().productId()).isEqualTo(8003L);
        assertThat(materials.get(0).node().lotNo()).isNull();
    }

    @Test
    void namesNonLotControlledOutputDownstream() {
        when(transactionMapper.selectList(any()))
                .thenReturn(List.of(txn(8001L, "LOT-A", "PRODUCTION_ISSUE", "MO-1", "OUT")))
                .thenReturn(List.of(txn(7002L, null, "PRODUCTION_COMPLETION", "MO-1", "IN")))
                .thenReturn(List.of());

        InventoryLotGenealogyQuery request = query(8001L, "LOT-A");
        request.setDirection("DOWNSTREAM");
        var produced = service.genealogy(request).downstream().links().get(0).node().links();

        assertThat(produced.get(0).terminalReason()).isEqualTo("OUTPUT_NOT_LOT_CONTROLLED");
        assertThat(produced.get(0).node().lotNo()).isNull();
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest=InventoryLotGenealogyServiceTest`
Expected: FAIL on most parameterized cases and both null-lot cases.

- [ ] **Step 3: Complete `terminalReason`**

```java
    private String terminalReason(Direction walk, String bizType) {
        String type = bizType == null ? "" : bizType.trim().toUpperCase(Locale.ROOT);
        if (walk == Direction.UPSTREAM) {
            return switch (type) {
                case "PURCHASE_RECEIPT" -> "PURCHASED";
                case "SALES_RETURN" -> "RETURNED_BY_CUSTOMER";
                case "PRODUCTION_RETURN", "INVENTORY_TRANSFER" -> "MOVED_INTERNALLY";
                case "INVENTORY_ADJUSTMENT", "INVENTORY_CHECK" -> "ADJUSTED";
                case "OPENING_BALANCE", "OPENING_INVENTORY" -> "OPENING_BALANCE";
                default -> "UNKNOWN_SOURCE";
            };
        }
        return switch (type) {
            case "SALES_DELIVERY" -> "SOLD";
            case "PURCHASE_RETURN" -> "RETURNED_TO_SUPPLIER";
            case "PRODUCTION_COMPLETION_REVERSAL" -> "REVERSED";
            case "INVENTORY_TRANSFER" -> "MOVED_INTERNALLY";
            case "INVENTORY_ADJUSTMENT", "INVENTORY_CHECK" -> "ADJUSTED";
            default -> "UNKNOWN_DESTINATION";
        };
    }
```

- [ ] **Step 4: Stop recursing into lots that have no lot number**

In the child fan-out inside `traverse`, before enqueuing `childNode`:

```java
                    if (!StringUtils.hasText(childKey.lotNo())) {
                        // lot_no is NULL for non-lot-controlled products, so the chain genuinely
                        // ends here. Name the product anyway rather than dropping the branch.
                        childLink.terminalReason = walk == Direction.UPSTREAM
                                ? "MATERIAL_NOT_LOT_CONTROLLED" : "OUTPUT_NOT_LOT_CONTROLLED";
                        orderNode.links.add(childLink);
                        continue;
                    }
```

Keep `childLink.child = childNode` set so the product is still named, and do **not** add it to `next` or to `visited`.

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=InventoryLotGenealogyServiceTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/tuowei/erp/inventory/stock/service/InventoryLotGenealogyService.java \
        backend/src/test/java/com/tuowei/erp/inventory/stock/InventoryLotGenealogyServiceTest.java
git commit -m "feat: map every lot genealogy terminal reason"
```

---

## Task 7: Guards — depth, cycles, node caps, and scope honesty

The spec calls this the feature's worst failure mode if wrong: presenting a warehouse-filtered recall list as complete.

**Files:**
- Modify: `service/InventoryLotGenealogyService.java`
- Test: `InventoryLotGenealogyServiceTest.java`

- [ ] **Step 1: Write the failing tests**

```java
    @Test
    void reportsMaxDepthWhenTheChainIsDeeperThanRequested() {
        // Every level produces another production order, so the walk can never finish.
        when(transactionMapper.selectList(any())).thenAnswer(invocation -> List.of(
                txn(7001L, "LOT-" + java.util.UUID.randomUUID(), "PRODUCTION_COMPLETION", "MO-X", "IN")));

        InventoryLotGenealogyQuery request = query(7001L, "LOT-F");
        request.setDirection("UPSTREAM");
        request.setMaxDepth(2);
        var limits = service.genealogy(request).limits();

        assertThat(limits.truncated()).isTrue();
        assertThat(limits.truncationReasons()).contains("MAX_DEPTH");
    }

    @Test
    void linksButDoesNotReexpandAlreadyVisitedLots() {
        // LOT-A is consumed by MO-1 whose output is LOT-A again: a legitimate return-then-reship cycle.
        when(transactionMapper.selectList(any()))
                .thenReturn(List.of(txn(7001L, "LOT-A", "PRODUCTION_COMPLETION", "MO-1", "IN")))
                .thenReturn(List.of(txn(7001L, "LOT-A", "PRODUCTION_ISSUE", "MO-1", "OUT")))
                .thenReturn(List.of());

        InventoryLotGenealogyQuery request = query(7001L, "LOT-A");
        request.setDirection("UPSTREAM");
        var materials = service.genealogy(request).upstream().links().get(0).node().links();

        assertThat(materials).hasSize(1);
        assertThat(materials.get(0).terminalReason()).isEqualTo("ALREADY_VISITED");
        // A cycle is not an error, so nothing is truncated by it.
        assertThat(service.genealogy(request).limits().truncationReasons()).doesNotContain("MAX_DEPTH");
    }

    @Test
    void capsFanOutPerLevelAndReportsIt() {
        List<InventoryTransactionEntity> wide = new java.util.ArrayList<>();
        for (int i = 0; i < 250; i++) {
            wide.add(txn(9000L + i, "LOT-W" + i, "PRODUCTION_ISSUE", "MO-1", "OUT"));
        }
        when(transactionMapper.selectList(any()))
                .thenReturn(List.of(txn(7001L, "LOT-F", "PRODUCTION_COMPLETION", "MO-1", "IN")))
                .thenReturn(wide)
                .thenReturn(List.of());

        InventoryLotGenealogyQuery request = query(7001L, "LOT-F");
        request.setDirection("UPSTREAM");
        var response = service.genealogy(request);

        assertThat(response.upstream().links().get(0).node().links()).hasSize(200);
        assertThat(response.limits().truncated()).isTrue();
        assertThat(response.limits().truncationReasons()).contains("NODE_LIMIT_PER_LEVEL");
        assertThat(response.limits().truncationReasons()).doesNotContain("NODE_LIMIT_TOTAL");
    }

    @Test
    void setsScopeLimitedForRestrictedCallers() {
        when(currentUserContext.requirePrincipal())
                .thenReturn(principal(new DataScopeSnapshot(false, false, false, false, Set.of(1L))));
        when(transactionMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.genealogy(query(7001L, "LOT-1")).limits().scopeLimited()).isTrue();
    }

    @Test
    void appliesDataScopeOnEveryLevelNotOnlyTheFirst() {
        when(transactionMapper.selectList(any()))
                .thenReturn(List.of(txn(7001L, "LOT-F", "PRODUCTION_COMPLETION", "MO-1", "IN")))
                .thenReturn(List.of(txn(8001L, "LOT-A", "PRODUCTION_ISSUE", "MO-1", "OUT")))
                .thenReturn(List.of())
                .thenReturn(List.of());

        InventoryLotGenealogyQuery request = query(7001L, "LOT-F");
        request.setDirection("UPSTREAM");
        service.genealogy(request);

        // Level 0, the MO counterpart query, and level 1 must each be scoped.
        org.mockito.Mockito.verify(dataScopeService, org.mockito.Mockito.atLeast(3))
                .applyInventoryTransactionScope(any(), any());
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest=InventoryLotGenealogyServiceTest`
Expected: FAIL — no caps and no `MAX_DEPTH` reporting yet (the `ALREADY_VISITED` case may already pass from Task 4).

- [ ] **Step 3: Implement the caps and the depth report**

Add a per-direction node counter and enforce both caps where children are enqueued:

```java
        int nodesInDirection = 1; // the root
        ...
            int acceptedThisLevel = 0;
            // inside the child fan-out, before enqueuing:
                    if (acceptedThisLevel >= PER_LEVEL_NODE_LIMIT) {
                        accumulator.truncationReasons.add("NODE_LIMIT_PER_LEVEL");
                        break;
                    }
                    if (nodesInDirection >= TOTAL_NODE_LIMIT) {
                        accumulator.truncationReasons.add("NODE_LIMIT_TOTAL");
                        break;
                    }
                    acceptedThisLevel++;
                    nodesInDirection++;
```

After the `while` loop, report unexpanded frontier as depth truncation:

```java
        if (!frontier.isEmpty()) {
            accumulator.truncationReasons.add("MAX_DEPTH");
            // Mark the links that point at the unexpanded leaves so the page can say why.
            for (NodeBuilder pending : frontier) {
                pending.truncatedByDepth = true;
            }
        }
```

Add `private boolean truncatedByDepth;` to `NodeBuilder`, and in `toLink`, when `builder.child != null && builder.child.truncatedByDepth && builder.terminalReason == null`, emit `terminalReason = "MAX_DEPTH"` while keeping the child node so the lot is still named.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=InventoryLotGenealogyServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/tuowei/erp/inventory/stock/service/InventoryLotGenealogyService.java \
        backend/src/test/java/com/tuowei/erp/inventory/stock/InventoryLotGenealogyServiceTest.java
git commit -m "feat: bound lot genealogy traversal and report every limit"
```

---

## Task 8: Endpoint and permission code

**Files:**
- Modify: `common/security/InventoryPermissionCodes.java`, `inventory/stock/controller/InventoryStockQueryController.java`
- Test: `backend/src/test/java/com/tuowei/erp/inventory/stock/InventoryLotGenealogyControllerTest.java`

**Interfaces:**
- Consumes: `InventoryLotGenealogyService.genealogy` (Task 2).
- Produces: `GET /api/inventory/lots/genealogy` returning `ApiResponse<InventoryLotGenealogyResponse>`; `PermissionCodes.HAS_INVENTORY_LOT_GENEALOGY`.

- [ ] **Step 1: Write the failing test**

Follow whatever style the existing controller tests in `inventory/stock` use (check for a `@WebMvcTest`-style or a plain-delegation test and match it). A plain delegation test plus a reflection assertion on the annotation:

```java
package com.tuowei.erp.inventory.stock;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.inventory.stock.controller.InventoryStockQueryController;
import com.tuowei.erp.inventory.stock.service.InventoryLotGenealogyService;
import com.tuowei.erp.inventory.stock.web.GenealogyLimits;
import com.tuowei.erp.inventory.stock.web.InventoryLotGenealogyQuery;
import com.tuowei.erp.inventory.stock.web.InventoryLotGenealogyResponse;
import com.tuowei.erp.inventory.stock.web.LotGenealogyNode;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryLotGenealogyControllerTest {

    @Test
    void exposesGenealogyUnderTheDedicatedPermissionCode() throws Exception {
        Method method = InventoryStockQueryController.class
                .getMethod("lotGenealogy", InventoryLotGenealogyQuery.class);

        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly("/lots/genealogy");
        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo(PermissionCodes.HAS_INVENTORY_LOT_GENEALOGY);
        assertThat(PermissionCodes.HAS_INVENTORY_LOT_GENEALOGY)
                .isEqualTo("hasAuthority('inventory:lot:genealogy')");
        assertThat(PermissionCodes.allPermissions()).contains("inventory:lot:genealogy");
    }

    @Test
    void delegatesToTheGenealogyService() {
        InventoryLotGenealogyResponse expected = new InventoryLotGenealogyResponse(
                new LotGenealogyNode(1L, "P", "N", "LOT", null, null, 0, List.of()),
                null, null,
                new GenealogyLimits(5, 200, 500, false, List.of(), false));
        InventoryLotGenealogyService service = org.mockito.Mockito.mock(InventoryLotGenealogyService.class);
        InventoryLotGenealogyQuery query = new InventoryLotGenealogyQuery();
        org.mockito.Mockito.when(service.genealogy(query)).thenReturn(expected);

        // Build the controller with the genealogy service in place; pass mocks for its other
        // constructor arguments, matching however the existing controller tests construct it.
        InventoryStockQueryController controller = InventoryStockQueryControllerFixtures.with(service);

        ApiResponse<InventoryLotGenealogyResponse> response = controller.lotGenealogy(query);
        assertThat(response.getData()).isSameAs(expected);
    }
}
```

If no fixture helper exists, drop the second test's helper and construct the controller inline with `Mockito.mock(...)` for each existing dependency. Read the controller's constructor first and mirror it.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=InventoryLotGenealogyControllerTest`
Expected: FAIL — no such method, no such permission constant.

- [ ] **Step 3: Add the permission constants**

In `InventoryPermissionCodes`, beside the serial entries:

```java
    String INVENTORY_LOT_GENEALOGY = "inventory:lot:genealogy";
```

and in the `HAS_` block:

```java
    String HAS_INVENTORY_LOT_GENEALOGY = "hasAuthority('" + INVENTORY_LOT_GENEALOGY + "')";
```

- [ ] **Step 4: Add the endpoint**

In `InventoryStockQueryController`, inject `InventoryLotGenealogyService` and add the method next to `traceLot`:

```java
    @PreAuthorize(PermissionCodes.HAS_INVENTORY_LOT_GENEALOGY)
    @GetMapping("/lots/genealogy")
    public ApiResponse<InventoryLotGenealogyResponse> lotGenealogy(InventoryLotGenealogyQuery query) {
        return ApiResponse.success(inventoryLotGenealogyService.genealogy(query));
    }
```

- [ ] **Step 5: Run the test plus the permission seed guards**

Run: `cd backend && ./mvnw test -Dtest='InventoryLotGenealogyControllerTest+CriticalPermissionSeedMigrationTest'`
Expected: PASS. If a test asserts that every seeded menu permission exists in `allPermissions()` (or the reverse), it may now fail until Task 9 seeds the menu — if so, run Tasks 8 and 9 back to back and commit them together.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/tuowei/erp/common/security/InventoryPermissionCodes.java \
        backend/src/main/java/com/tuowei/erp/inventory/stock/controller/InventoryStockQueryController.java \
        backend/src/test/java/com/tuowei/erp/inventory/stock/InventoryLotGenealogyControllerTest.java
git commit -m "feat: expose lot genealogy endpoint"
```

---

## Task 9: V145 menu migration

**Files:**
- Create: `backend/src/main/resources/db/migration/V145__inventory_lot_genealogy_menu.sql`
- Test: `backend/src/test/java/com/tuowei/erp/db/InventoryLotGenealogyMenuMigrationTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.tuowei.erp.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryLotGenealogyMenuMigrationTest {

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateSchema() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:inventory_lot_genealogy;MODE=MySQL;"
                + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Path migrationDir = H2MigrationTestSupport.copyCompatibleMigrations(
                InventoryLotGenealogyMenuMigrationTest.class, "inventory-lot-genealogy-migrations");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationDir.toAbsolutePath().toString().replace('\\', '/'))
                .load()
                .migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void v145SeedsTheGenealogyMenuUnderTheInventoryCatalog() {
        Map<String, Object> menu = jdbcTemplate.queryForMap("""
                select parent_id, menu_type, path, component, permission, status, deleted_flag
                from sys_menu where id = 5480
                """);

        assertThat(menu.get("parent_id")).isEqualTo(5009L);
        assertThat(menu.get("menu_type")).isEqualTo("MENU");
        assertThat(menu.get("path")).isEqualTo("/inventory/lot-genealogy");
        assertThat(menu.get("component")).isEqualTo("inventory/lot-genealogy/index");
        assertThat(menu.get("permission")).isEqualTo("inventory:lot:genealogy");
        assertThat(menu.get("status")).isEqualTo("ACTIVE");
        assertThat(((Number) menu.get("deleted_flag")).intValue()).isZero();
    }

    @Test
    void v145BindsTheMenuToErpAdmin() {
        Integer bound = jdbcTemplate.queryForObject("""
                select count(*) from sys_role_menu where id = 7490 and role_id = 3002 and menu_id = 5480
                """, Integer.class);
        assertThat(bound).isEqualTo(1);
    }

    @Test
    void v145DoesNotCollideWithExistingSeedIds() {
        Integer menuDuplicates = jdbcTemplate.queryForObject("""
                select count(*) from sys_menu where menu_code = 'INVENTORY_LOT_GENEALOGY'
                """, Integer.class);
        assertThat(menuDuplicates).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=InventoryLotGenealogyMenuMigrationTest`
Expected: FAIL — `EmptyResultDataAccessException`, no row with id 5480.

- [ ] **Step 3: Write the migration**

`V145__inventory_lot_genealogy_menu.sql`, following V117's shape and V98's idempotency:

```sql
-- V145: 批次谱系菜单（挂库存 CATALOG 5009）
INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5480, 5009, 'MENU', 'INVENTORY_LOT_GENEALOGY', '批次谱系', '/inventory/lot-genealogy',
     'inventory/lot-genealogy/index', 'inventory:lot:genealogy', 30, 1, 'ACTIVE', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_name = VALUES(menu_name),
    path = VALUES(path),
    component = VALUES(component),
    permission = VALUES(permission),
    status = VALUES(status),
    deleted_flag = VALUES(deleted_flag);

INSERT INTO sys_role_menu (id, role_id, menu_id, created_by) VALUES
    (7490, 3002, 5480, 0)
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id), menu_id = VALUES(menu_id);
```

- [ ] **Step 4: Run the migration tests and the alignment guard**

Run: `cd backend && ./mvnw test -Dtest='InventoryLotGenealogyMenuMigrationTest+RuntimeMenuRouteAlignmentMigrationTest+FlywayMigrationSmokeTest'`
Expected: PASS. `RuntimeMenuRouteAlignmentMigrationTest` may require the frontend route to exist conceptually; if it asserts a component allowlist, add the new component there in the same commit.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V145__inventory_lot_genealogy_menu.sql \
        backend/src/test/java/com/tuowei/erp/db/InventoryLotGenealogyMenuMigrationTest.java
git commit -m "feat: seed lot genealogy menu and permission"
```

---

## Task 10: Frontend API client

**Files:**
- Modify: `frontend/src/api/inventory.ts`

**Interfaces:**
- Produces: types `LotGenealogyCounterparty`, `LotGenealogyLink`, `LotGenealogyNode`, `GenealogyLimits`, `InventoryLotGenealogy`, `InventoryLotGenealogyQuery`, and `getInventoryLotGenealogy(params) → Promise<InventoryLotGenealogy>`. Tasks 11–14 consume these.

- [ ] **Step 1: Add the types and the request function**

Append near `getInventoryLotTrace`. Property names must match the backend records exactly (Task 2).

```ts
export type LotGenealogyCounterparty = {
  type: 'SUPPLIER' | 'CUSTOMER'
  id: string | number
  code: string | null
  name: string | null
  documentNo: string | null
}

export type LotGenealogyLink = {
  bizType: string
  bizNo: string | null
  bizLabel: string | null
  documentRoute: string | null
  occurredTime: string | null
  qty: string | number | null
  warehouseId: string | number | null
  warehouseName: string | null
  counterparty: LotGenealogyCounterparty | null
  terminalReason: string | null
  node: LotGenealogyNode | null
}

export type LotGenealogyNode = {
  productId: string | number
  productCode: string | null
  productName: string | null
  lotNo: string | null
  productionDate: string | null
  expiryDate: string | null
  depth: number
  links: LotGenealogyLink[]
}

export type GenealogyLimits = {
  maxDepth: number
  perLevelNodeLimit: number
  totalNodeLimit: number
  truncated: boolean
  truncationReasons: string[]
  scopeLimited: boolean
}

export type InventoryLotGenealogy = {
  root: LotGenealogyNode
  upstream: LotGenealogyNode | null
  downstream: LotGenealogyNode | null
  limits: GenealogyLimits
}

export type InventoryLotGenealogyQuery = {
  productId: string | number
  lotNo: string
  direction?: 'UPSTREAM' | 'DOWNSTREAM' | 'BOTH'
  maxDepth?: number
}

export const getInventoryLotGenealogy = (params: InventoryLotGenealogyQuery) => {
  return request.get<InventoryLotGenealogy>('/inventory/lots/genealogy', { params })
}
```

`LotGenealogyLink` and `LotGenealogyNode` reference each other, which TypeScript allows for `type` aliases.

- [ ] **Step 2: Verify types compile**

Run: `cd frontend && npm run type-check`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/api/inventory.ts
git commit -m "feat: add lot genealogy api client"
```

---

## Task 11: useInventoryLotGenealogyPresentation

**Files:**
- Create: `frontend/src/composables/useInventoryLotGenealogyPresentation.ts`
- Test: `frontend/src/composables/useInventoryLotGenealogyPresentation.test.ts`

**Interfaces:**
- Produces: `useInventoryLotGenealogyPresentation(t, options)` returning `{ bizTypeLabel, terminalReasonLabel, terminalReasonType, counterpartyLabel, lotLabel, productLabel, formatQty, formatDateTime, truncationBanner, scopeBanner }`. Task 12 and the page consume these.

- [ ] **Step 1: Write the failing test**

```ts
import { describe, expect, it } from 'vitest'

import { useInventoryLotGenealogyPresentation } from './useInventoryLotGenealogyPresentation'

const t = (key: string, params?: Record<string, unknown>) =>
  params ? `${key}:${JSON.stringify(params)}` : key

const presentation = () =>
  useInventoryLotGenealogyPresentation(t, {
    formatNumber: (value) => `#${value}`,
    formatDateTime: (value) => `@${value}`
  })

describe('useInventoryLotGenealogyPresentation', () => {
  it('labels biz types through i18n rather than the backend string', () => {
    const { bizTypeLabel } = presentation()
    // Correction 2 in the spec: the backend label is Chinese-only, so it is a fallback only.
    expect(bizTypeLabel({ bizType: 'PURCHASE_RECEIPT', bizLabel: '采购收货' })).toBe(
      'inventoryLotGenealogy.bizType.purchaseReceipt'
    )
    expect(bizTypeLabel({ bizType: 'MYSTERY', bizLabel: '神秘单据' })).toBe('神秘单据')
    expect(bizTypeLabel({ bizType: 'MYSTERY', bizLabel: null })).toBe('MYSTERY')
  })

  it('labels every terminal reason in the closed set', () => {
    const { terminalReasonLabel } = presentation()
    expect(terminalReasonLabel('PURCHASED')).toBe('inventoryLotGenealogy.reason.purchased')
    expect(terminalReasonLabel('SOLD')).toBe('inventoryLotGenealogy.reason.sold')
    expect(terminalReasonLabel('IN_PRODUCTION')).toBe('inventoryLotGenealogy.reason.inProduction')
    expect(terminalReasonLabel('MATERIAL_NOT_LOT_CONTROLLED')).toBe(
      'inventoryLotGenealogy.reason.materialNotLotControlled'
    )
    expect(terminalReasonLabel('MAX_DEPTH')).toBe('inventoryLotGenealogy.reason.maxDepth')
    expect(terminalReasonLabel(null)).toBe('')
    expect(terminalReasonLabel('SOMETHING_ELSE')).toBe('SOMETHING_ELSE')
  })

  it('distinguishes recall-relevant reasons from incomplete ones by tag type', () => {
    const { terminalReasonType } = presentation()
    expect(terminalReasonType('SOLD')).toBe('danger')
    expect(terminalReasonType('PURCHASED')).toBe('success')
    expect(terminalReasonType('MAX_DEPTH')).toBe('warning')
    expect(terminalReasonType('MOVED_INTERNALLY')).toBe('info')
  })

  it('renders counterparties with code, name and source document', () => {
    const { counterpartyLabel } = presentation()
    expect(
      counterpartyLabel({ type: 'CUSTOMER', id: 1, code: 'C-1', name: '客户甲', documentNo: 'SO-1' })
    ).toBe('C-1 客户甲')
    expect(counterpartyLabel(null)).toBe('-')
  })

  it('formats quantities and times through the injected preference helpers', () => {
    const { formatQty, formatDateTime } = presentation()
    expect(formatQty('10')).toBe('#10')
    expect(formatQty(null)).toBe('-')
    expect(formatDateTime('2026-08-01T10:00:00')).toBe('@2026-08-01T10:00:00')
    expect(formatDateTime(null)).toBe('-')
  })

  it('builds banners only when a limit actually fired', () => {
    const { truncationBanner, scopeBanner } = presentation()
    expect(truncationBanner({ truncated: false, truncationReasons: [] })).toBeNull()
    expect(
      truncationBanner({ truncated: true, truncationReasons: ['MAX_DEPTH', 'NODE_LIMIT_TOTAL'] })
    ).toBe(
      'inventoryLotGenealogy.banner.truncated:{"reasons":"inventoryLotGenealogy.reason.maxDepth、inventoryLotGenealogy.reason.nodeLimitTotal"}'
    )
    expect(scopeBanner({ scopeLimited: false })).toBeNull()
    expect(scopeBanner({ scopeLimited: true })).toBe('inventoryLotGenealogy.banner.scopeLimited')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/composables/useInventoryLotGenealogyPresentation.test.ts`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement the composable**

```ts
import type { GenealogyLimits, LotGenealogyCounterparty } from '@/api/inventory'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'info' | 'warning' | 'danger' | 'primary'

const BIZ_TYPE_KEYS: Record<string, string> = {
  PURCHASE_RECEIPT: 'purchaseReceipt',
  PURCHASE_RETURN: 'purchaseReturn',
  SALES_DELIVERY: 'salesDelivery',
  SALES_RETURN: 'salesReturn',
  PRODUCTION_ISSUE: 'productionIssue',
  PRODUCTION_COMPLETION: 'productionCompletion',
  PRODUCTION_COMPLETION_REVERSAL: 'productionCompletionReversal',
  PRODUCTION_RETURN: 'productionReturn',
  INVENTORY_ADJUSTMENT: 'inventoryAdjustment',
  INVENTORY_TRANSFER: 'inventoryTransfer',
  INVENTORY_CHECK: 'inventoryCheck',
  OPENING_BALANCE: 'openingBalance',
  OPENING_INVENTORY: 'openingBalance'
}

const REASON_KEYS: Record<string, string> = {
  PURCHASED: 'purchased',
  SOLD: 'sold',
  RETURNED_BY_CUSTOMER: 'returnedByCustomer',
  RETURNED_TO_SUPPLIER: 'returnedToSupplier',
  MOVED_INTERNALLY: 'movedInternally',
  ADJUSTED: 'adjusted',
  OPENING_BALANCE: 'openingBalance',
  REVERSED: 'reversed',
  IN_PRODUCTION: 'inProduction',
  NO_MATERIAL_ISSUED: 'noMaterialIssued',
  MATERIAL_NOT_LOT_CONTROLLED: 'materialNotLotControlled',
  OUTPUT_NOT_LOT_CONTROLLED: 'outputNotLotControlled',
  ALREADY_VISITED: 'alreadyVisited',
  MAX_DEPTH: 'maxDepth',
  NODE_LIMIT_PER_LEVEL: 'nodeLimitPerLevel',
  NODE_LIMIT_TOTAL: 'nodeLimitTotal',
  UNKNOWN_SOURCE: 'unknownSource',
  UNKNOWN_DESTINATION: 'unknownDestination'
}

const REASON_TAGS: Record<string, TagType> = {
  SOLD: 'danger',
  RETURNED_TO_SUPPLIER: 'danger',
  PURCHASED: 'success',
  RETURNED_BY_CUSTOMER: 'success',
  MAX_DEPTH: 'warning',
  NODE_LIMIT_PER_LEVEL: 'warning',
  NODE_LIMIT_TOTAL: 'warning',
  IN_PRODUCTION: 'warning',
  UNKNOWN_SOURCE: 'warning',
  UNKNOWN_DESTINATION: 'warning'
}

/** Display helpers for the inventory lot genealogy page. */
export const useInventoryLotGenealogyPresentation = (
  t: Translate,
  options: {
    formatNumber: (value: string | number) => string
    formatDateTime: (value: string) => string
  }
) => {
  const bizTypeLabel = (link: { bizType: string; bizLabel?: string | null }) => {
    const key = BIZ_TYPE_KEYS[String(link.bizType || '').toUpperCase()]
    if (key) return t(`inventoryLotGenealogy.bizType.${key}`)
    return link.bizLabel || link.bizType || '-'
  }

  const terminalReasonLabel = (reason?: string | null) => {
    if (!reason) return ''
    const key = REASON_KEYS[reason]
    return key ? t(`inventoryLotGenealogy.reason.${key}`) : reason
  }

  const terminalReasonType = (reason?: string | null): TagType =>
    REASON_TAGS[String(reason || '')] || 'info'

  const counterpartyLabel = (counterparty?: LotGenealogyCounterparty | null) => {
    if (!counterparty) return '-'
    return [counterparty.code, counterparty.name].filter(Boolean).join(' ') || '-'
  }

  const lotLabel = (lotNo?: string | null) =>
    lotNo || t('inventoryLotGenealogy.noLot')

  const productLabel = (node: { productCode?: string | null; productName?: string | null; productId: string | number }) =>
    [node.productCode, node.productName].filter(Boolean).join(' ') || String(node.productId)

  const formatQty = (value?: string | number | null) =>
    value == null || value === '' ? '-' : options.formatNumber(value)

  const formatDateTime = (value?: string | null) =>
    value ? options.formatDateTime(value) : '-'

  const truncationBanner = (limits: Pick<GenealogyLimits, 'truncated' | 'truncationReasons'>) => {
    if (!limits.truncated || limits.truncationReasons.length === 0) return null
    const reasons = limits.truncationReasons.map(terminalReasonLabel).join('、')
    return t('inventoryLotGenealogy.banner.truncated', { reasons })
  }

  const scopeBanner = (limits: Pick<GenealogyLimits, 'scopeLimited'>) =>
    limits.scopeLimited ? t('inventoryLotGenealogy.banner.scopeLimited') : null

  return {
    bizTypeLabel,
    terminalReasonLabel,
    terminalReasonType,
    counterpartyLabel,
    lotLabel,
    productLabel,
    formatQty,
    formatDateTime,
    truncationBanner,
    scopeBanner
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/composables/useInventoryLotGenealogyPresentation.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/composables/useInventoryLotGenealogyPresentation.ts \
        frontend/src/composables/useInventoryLotGenealogyPresentation.test.ts
git commit -m "feat: add lot genealogy presentation composable"
```

---

## Task 12: useInventoryLotGenealogyTree

**Files:**
- Create: `frontend/src/composables/useInventoryLotGenealogyTree.ts`
- Test: `frontend/src/composables/useInventoryLotGenealogyTree.test.ts`

**Interfaces:**
- Consumes: presentation helpers (Task 11), `LotGenealogyNode` (Task 10).
- Produces: `useInventoryLotGenealogyTree(t, options)` returning `{ toTreeData, recallRows, recallHeaders }`. Tree nodes have shape `{ id: string; label: string; detail: string; reason: string; reasonType: string; route: string | null; children: TreeNode[] }`.

- [ ] **Step 1: Write the failing test**

```ts
import { describe, expect, it } from 'vitest'

import type { LotGenealogyNode } from '@/api/inventory'
import { useInventoryLotGenealogyTree } from './useInventoryLotGenealogyTree'

const t = (key: string) => key

const tree = () =>
  useInventoryLotGenealogyTree(t, {
    bizTypeLabel: (link) => `T(${link.bizType})`,
    terminalReasonLabel: (reason) => (reason ? `R(${reason})` : ''),
    terminalReasonType: () => 'info',
    counterpartyLabel: (counterparty) => (counterparty ? String(counterparty.name) : '-'),
    productLabel: (node) => `P(${node.productId})`,
    lotLabel: (lotNo) => lotNo || 'NO_LOT',
    formatQty: (value) => `Q(${value})`,
    formatDateTime: (value) => `D(${value})`
  })

const sold: LotGenealogyNode = {
  productId: 7001,
  productCode: 'P-7001',
  productName: '成品甲',
  lotNo: 'LOT-F',
  productionDate: null,
  expiryDate: null,
  depth: 0,
  links: [
    {
      bizType: 'SALES_DELIVERY',
      bizNo: 'SD-1',
      bizLabel: '销售发货',
      documentRoute: '/sales/deliveries?keyword=SD-1',
      occurredTime: '2026-08-01T10:00:00',
      qty: '10',
      warehouseId: 1,
      warehouseName: '主仓',
      counterparty: { type: 'CUSTOMER', id: 601, code: 'C-601', name: '客户甲', documentNo: 'SO-1' },
      terminalReason: 'SOLD',
      node: null
    }
  ]
}

describe('useInventoryLotGenealogyTree', () => {
  it('maps a node and its links into el-tree data with stable ids', () => {
    const data = tree().toTreeData(sold, 'DOWNSTREAM')

    expect(data).toHaveLength(1)
    expect(data[0].label).toBe('P(7001) LOT-F')
    expect(data[0].children).toHaveLength(1)
    const link = data[0].children[0]
    expect(link.label).toBe('T(SALES_DELIVERY) SD-1')
    expect(link.reason).toBe('R(SOLD)')
    expect(link.route).toBe('/sales/deliveries?keyword=SD-1')
    expect(link.detail).toContain('客户甲')
    expect(link.detail).toContain('Q(10)')
    // Ids must be unique and deterministic so el-tree expansion state survives a refresh.
    expect(link.id).toBe('DOWNSTREAM-1-SALES_DELIVERY-SD-1-0')
    expect(link.children).toHaveLength(0)
  })

  it('recurses into child nodes', () => {
    const nested: LotGenealogyNode = {
      ...sold,
      links: [{ ...sold.links[0], terminalReason: null, node: { ...sold, depth: 1, links: [] } }]
    }

    const data = tree().toTreeData(nested, 'DOWNSTREAM')
    expect(data[0].children[0].children).toHaveLength(1)
    expect(data[0].children[0].children[0].label).toBe('P(7001) LOT-F')
  })

  it('flattens only real customer deliveries into the recall list', () => {
    const nested: LotGenealogyNode = {
      ...sold,
      links: [
        sold.links[0],
        {
          ...sold.links[0],
          bizType: 'INVENTORY_TRANSFER',
          bizNo: 'IT-1',
          counterparty: null,
          terminalReason: 'MOVED_INTERNALLY'
        }
      ]
    }

    const rows = tree().recallRows(nested)

    // An internal transfer has no customer, so it is not a recall contact.
    expect(rows).toHaveLength(1)
    expect(rows[0]).toEqual([
      'P(7001)',
      'LOT-F',
      'T(SALES_DELIVERY)',
      'SD-1',
      'SO-1',
      'C-601',
      '客户甲',
      'Q(10)',
      'D(2026-08-01T10:00:00)'
    ])
  })

  it('returns an empty recall list for a null tree', () => {
    expect(tree().recallRows(null)).toEqual([])
  })

  it('exposes translated recall headers matching the row width', () => {
    const { recallHeaders, recallRows } = tree()
    expect(recallHeaders()).toHaveLength(recallRows(sold)[0].length)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/composables/useInventoryLotGenealogyTree.test.ts`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement the composable**

```ts
import type { LotGenealogyCounterparty, LotGenealogyLink, LotGenealogyNode } from '@/api/inventory'

type Translate = (key: string, params?: Record<string, unknown>) => string

export type GenealogyTreeNode = {
  id: string
  label: string
  detail: string
  reason: string
  reasonType: string
  route: string | null
  children: GenealogyTreeNode[]
}

export const useInventoryLotGenealogyTree = (
  t: Translate,
  options: {
    bizTypeLabel: (link: { bizType: string; bizLabel?: string | null }) => string
    terminalReasonLabel: (reason?: string | null) => string
    terminalReasonType: (reason?: string | null) => string
    counterpartyLabel: (counterparty?: LotGenealogyCounterparty | null) => string
    productLabel: (node: LotGenealogyNode) => string
    lotLabel: (lotNo?: string | null) => string
    formatQty: (value?: string | number | null) => string
    formatDateTime: (value?: string | null) => string
  }
) => {
  const linkDetail = (link: LotGenealogyLink) =>
    [
      options.counterpartyLabel(link.counterparty),
      options.formatQty(link.qty),
      options.formatDateTime(link.occurredTime),
      link.warehouseName || ''
    ]
      .filter((part) => part && part !== '-')
      .join(' · ')

  const mapLink = (
    link: LotGenealogyLink,
    direction: string,
    depth: number,
    index: number
  ): GenealogyTreeNode => ({
    id: `${direction}-${depth}-${link.bizType}-${link.bizNo ?? ''}-${index}`,
    label: [options.bizTypeLabel(link), link.bizNo].filter(Boolean).join(' '),
    detail: linkDetail(link),
    reason: options.terminalReasonLabel(link.terminalReason),
    reasonType: options.terminalReasonType(link.terminalReason),
    route: link.documentRoute ?? null,
    children: link.node ? mapNode(link.node, direction, depth + 1) : []
  })

  const mapNode = (
    node: LotGenealogyNode,
    direction: string,
    depth: number
  ): GenealogyTreeNode[] => [
    {
      id: `${direction}-${depth}-${node.productId}-${node.lotNo ?? ''}`,
      label: `${options.productLabel(node)} ${options.lotLabel(node.lotNo)}`,
      detail: '',
      reason: '',
      reasonType: 'info',
      route: null,
      children: node.links.map((link, index) => mapLink(link, direction, depth, index))
    }
  ]

  const toTreeData = (node: LotGenealogyNode | null, direction: string): GenealogyTreeNode[] =>
    node ? mapNode(node, direction, 1) : []

  const recallHeaders = () => [
    t('inventoryLotGenealogy.recall.product'),
    t('inventoryLotGenealogy.recall.lotNo'),
    t('inventoryLotGenealogy.recall.bizType'),
    t('inventoryLotGenealogy.recall.bizNo'),
    t('inventoryLotGenealogy.recall.orderNo'),
    t('inventoryLotGenealogy.recall.counterpartyCode'),
    t('inventoryLotGenealogy.recall.counterpartyName'),
    t('inventoryLotGenealogy.recall.qty'),
    t('inventoryLotGenealogy.recall.occurredTime')
  ]

  /** Flattens the downstream tree into one row per delivery that reached a named counterparty. */
  const recallRows = (node: LotGenealogyNode | null): Array<Array<string>> => {
    if (!node) return []
    const rows: Array<Array<string>> = []
    const walk = (current: LotGenealogyNode) => {
      for (const link of current.links) {
        if (link.counterparty) {
          rows.push([
            options.productLabel(current),
            current.lotNo ?? '',
            options.bizTypeLabel(link),
            link.bizNo ?? '',
            link.counterparty.documentNo ?? '',
            link.counterparty.code ?? '',
            link.counterparty.name ?? '',
            options.formatQty(link.qty),
            options.formatDateTime(link.occurredTime)
          ])
        }
        if (link.node) walk(link.node)
      }
    }
    walk(node)
    return rows
  }

  return { toTreeData, recallRows, recallHeaders }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/composables/useInventoryLotGenealogyTree.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/composables/useInventoryLotGenealogyTree.ts \
        frontend/src/composables/useInventoryLotGenealogyTree.test.ts
git commit -m "feat: map lot genealogy response into tree and recall rows"
```

---

## Task 13: useInventoryLotGenealogyQuery

**Files:**
- Create: `frontend/src/composables/useInventoryLotGenealogyQuery.ts`
- Test: `frontend/src/composables/useInventoryLotGenealogyQuery.test.ts`

**Interfaces:**
- Consumes: `getInventoryLotGenealogy` (Task 10).
- Produces: `useInventoryLotGenealogyQuery(t, options)` returning `{ form, loading, genealogy, load, reset, applyFromRoute, exportRecall }`.

- [ ] **Step 1: Write the failing test**

```ts
import { describe, expect, it, vi } from 'vitest'

import { useInventoryLotGenealogyQuery } from './useInventoryLotGenealogyQuery'

const t = (key: string) => key

const emptyResponse = {
  root: { productId: 1, productCode: null, productName: null, lotNo: 'LOT-1', productionDate: null, expiryDate: null, depth: 0, links: [] },
  upstream: null,
  downstream: null,
  limits: { maxDepth: 5, perLevelNodeLimit: 200, totalNodeLimit: 500, truncated: false, truncationReasons: [], scopeLimited: false }
}

describe('useInventoryLotGenealogyQuery', () => {
  it('requires a product and a lot before calling the API', async () => {
    const getInventoryLotGenealogy = vi.fn()
    const onError = vi.fn()
    const { load } = useInventoryLotGenealogyQuery(t, { getInventoryLotGenealogy, onError })

    await load()

    expect(getInventoryLotGenealogy).not.toHaveBeenCalled()
    expect(onError).toHaveBeenCalledWith('inventoryLotGenealogy.feedback.productAndLotRequired')
  })

  it('sends the trimmed lot, direction and depth', async () => {
    const getInventoryLotGenealogy = vi.fn().mockResolvedValue(emptyResponse)
    const { form, load, genealogy } = useInventoryLotGenealogyQuery(t, { getInventoryLotGenealogy })

    form.productId = 7001
    form.lotNo = '  LOT-1  '
    form.direction = 'UPSTREAM'
    form.maxDepth = 3
    await load()

    expect(getInventoryLotGenealogy).toHaveBeenCalledWith({
      productId: 7001,
      lotNo: 'LOT-1',
      direction: 'UPSTREAM',
      maxDepth: 3
    })
    expect(genealogy.value).toEqual(emptyResponse)
  })

  it('ignores a slow response that a newer query has superseded', async () => {
    let resolveFirst: (value: unknown) => void = () => {}
    const first = new Promise((resolve) => {
      resolveFirst = resolve
    })
    const second = { ...emptyResponse, limits: { ...emptyResponse.limits, maxDepth: 9 } }
    const getInventoryLotGenealogy = vi
      .fn()
      .mockReturnValueOnce(first)
      .mockResolvedValueOnce(second)

    const { form, load, genealogy } = useInventoryLotGenealogyQuery(t, { getInventoryLotGenealogy })
    form.productId = 7001
    form.lotNo = 'LOT-1'

    const firstLoad = load()
    const secondLoad = load()
    await secondLoad
    resolveFirst({ ...emptyResponse, limits: { ...emptyResponse.limits, maxDepth: 1 } })
    await firstLoad

    // The stale first response must not repaint over the newer one.
    expect(genealogy.value?.limits.maxDepth).toBe(9)
  })

  it('reports failures and clears loading', async () => {
    const getInventoryLotGenealogy = vi.fn().mockRejectedValue(new Error('boom'))
    const onError = vi.fn()
    const { form, load, loading } = useInventoryLotGenealogyQuery(t, {
      getInventoryLotGenealogy,
      onError
    })

    form.productId = 7001
    form.lotNo = 'LOT-1'
    await load()

    expect(onError).toHaveBeenCalledWith('inventoryLotGenealogy.feedback.loadFailed')
    expect(loading.value).toBe(false)
  })

  it('seeds the form from route query so the stocks dialog can escalate', () => {
    const { form, applyFromRoute } = useInventoryLotGenealogyQuery(t, {
      getInventoryLotGenealogy: vi.fn()
    })

    applyFromRoute({ productId: '7001', lotNo: 'LOT-1' })

    expect(form.productId).toBe('7001')
    expect(form.lotNo).toBe('LOT-1')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/composables/useInventoryLotGenealogyQuery.test.ts`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement the composable**

```ts
import { reactive, ref } from 'vue'

import type { InventoryLotGenealogy, InventoryLotGenealogyQuery } from '@/api/inventory'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export const useInventoryLotGenealogyQuery = (
  t: Translate,
  options: {
    getInventoryLotGenealogy: (params: InventoryLotGenealogyQuery) => Promise<InventoryLotGenealogy>
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const loading = ref(false)
  const genealogy = ref<InventoryLotGenealogy | null>(null)
  // Monotonic token so a slow response cannot repaint over a newer query.
  let requestSeq = 0

  const form = reactive({
    productId: '' as string | number,
    lotNo: '',
    direction: 'BOTH' as 'UPSTREAM' | 'DOWNSTREAM' | 'BOTH',
    maxDepth: 5
  })

  const load = async () => {
    const lotNo = form.lotNo.trim()
    if (form.productId === '' || form.productId == null || !lotNo) {
      options.onError?.(t('inventoryLotGenealogy.feedback.productAndLotRequired'))
      return
    }
    const seq = ++requestSeq
    loading.value = true
    try {
      const response = await options.getInventoryLotGenealogy({
        productId: form.productId,
        lotNo,
        direction: form.direction,
        maxDepth: form.maxDepth
      })
      if (seq !== requestSeq) return
      genealogy.value = response
    } catch {
      if (seq === requestSeq) {
        options.onError?.(t('inventoryLotGenealogy.feedback.loadFailed'))
      }
    } finally {
      if (seq === requestSeq) loading.value = false
    }
  }

  const reset = () => {
    form.productId = ''
    form.lotNo = ''
    form.direction = 'BOTH'
    form.maxDepth = 5
    genealogy.value = null
  }

  const applyFromRoute = (query: Record<string, unknown>) => {
    if (query.productId != null && query.productId !== '') {
      form.productId = String(query.productId)
    }
    if (query.lotNo != null && query.lotNo !== '') {
      form.lotNo = String(query.lotNo)
    }
  }

  return { form, loading, genealogy, load, reset, applyFromRoute }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/composables/useInventoryLotGenealogyQuery.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/composables/useInventoryLotGenealogyQuery.ts \
        frontend/src/composables/useInventoryLotGenealogyQuery.test.ts
git commit -m "feat: add lot genealogy query composable"
```

---

## Task 14: Page, route, and i18n

**Files:**
- Create: `frontend/src/views/inventory/lot-genealogy/index.vue`
- Modify: `frontend/src/router/index.ts`, `frontend/src/i18n/operations-pages.ts`, `frontend/src/i18n/operations-pages.test.ts`

- [ ] **Step 1: Add the i18n namespace**

In `operations-pages.ts`, add an `inventoryLotGenealogy` namespace to **both** `'zh-CN'` and `'en-US'` with identical key trees. Required leaves (referenced by Tasks 11–13 and the template below):

`title`, `noLot`, `upstream`, `downstream`, `recallList`, `empty`,
`field.product`, `field.lotNo`, `field.direction`, `field.maxDepth`,
`placeholder.product`, `placeholder.lotNo`, `placeholder.direction`,
`direction.upstream`, `direction.downstream`, `direction.both`,
`action.search`, `action.reset`, `action.exportRecall`, `action.openDocument`,
`bizType.*` — the 12 keys in `BIZ_TYPE_KEYS`,
`reason.*` — the 18 keys in `REASON_KEYS`,
`banner.truncated` (must contain `{reasons}`), `banner.scopeLimited`,
`recall.product`, `recall.lotNo`, `recall.bizType`, `recall.bizNo`, `recall.orderNo`, `recall.counterpartyCode`, `recall.counterpartyName`, `recall.qty`, `recall.occurredTime`, `recall.filename`,
`feedback.productAndLotRequired`, `feedback.loadFailed`, `feedback.exportEmpty`.

English values must contain no CJK characters — `modular-page-messages.test.ts` fails the build otherwise.

- [ ] **Step 2: Register the namespace and the component in the i18n guard**

In `operations-pages.test.ts`, add `'inventoryLotGenealogy'` to `expectedNamespaces` and `'src/views/inventory/lot-genealogy/index.vue'` to `componentPaths`.

- [ ] **Step 3: Run the i18n tests to verify they fail**

Run: `cd frontend && npx vitest run src/i18n/`
Expected: FAIL — the component file does not exist yet, and/or namespace keys are missing.

- [ ] **Step 4: Build the page**

`views/inventory/lot-genealogy/index.vue`. Compose the three composables, take `t` from `useI18n()`, and take the formatters from `utils/locale` (`formatLocalizedNumber`, `formatLocalizedDateTime`, `readDisplayPreferences`) exactly as a sibling inventory page does — copy that wiring rather than inventing it. Every visible string goes through `t(...)`; no literal Chinese in the template.

Structure:
- A query bar: product selector (reuse whatever product-picker the sibling inventory pages use), lot input, direction select, depth input, search and reset buttons.
- Two `el-alert` banners bound to `truncationBanner(...)` and `scopeBanner(...)`, each rendered only when non-null.
- Two `el-tree` panels for upstream and downstream, `:data="toTreeData(genealogy.upstream, 'UPSTREAM')"` and the downstream equivalent, with a custom node slot rendering `label`, `detail`, an `el-tag` of `reason`/`reasonType`, and a document link that calls `router.push(node.route)` when `route` is set.
- An export button that builds CSV from `recallRows(genealogy.downstream)` and `recallHeaders()`, using the established idiom — `escapeCell`, a `﻿` BOM prefix, and `downloadBlob` from `@/utils/download`, copied from `useProductList.ts`'s `exportSelectedRowsToCsv`. When `recallRows` is empty, surface `feedback.exportEmpty` instead of downloading an empty file.
- On mount, call `applyFromRoute(route.query)` and, when both fields arrived, `load()`.

- [ ] **Step 5: Add the route**

In `router/index.ts`, inside the `Inventory` children array after `replenishment-suggestions`:

```ts
          {
            path: 'lot-genealogy',
            name: 'InventoryLotGenealogy',
            component: () => import('@/views/inventory/lot-genealogy/index.vue'),
            meta: {
              title: '批次谱系',
              icon: 'Share',
              permission: 'inventory:lot:genealogy'
            }
          }
```

The `path`, `component`, and `permission` must match V145's `path`, `component`, and `permission` columns exactly, or V126's runtime menu alignment filters the node out of the sidebar.

- [ ] **Step 6: Run the full frontend gate**

Run: `cd frontend && npm run lint && npm run type-check && npx vitest run && npm run check:contracts`
Expected: all PASS.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/views/inventory/lot-genealogy/index.vue \
        frontend/src/router/index.ts \
        frontend/src/i18n/operations-pages.ts \
        frontend/src/i18n/operations-pages.test.ts
git commit -m "feat: add inventory lot genealogy page"
```

---

## Task 15: Escalate from the lot trace dialog

The spec requires an operator to reach genealogy from movement history without retyping the lot.

**Files:**
- Modify: `frontend/src/views/inventory/stocks/index.vue`

- [ ] **Step 1: Add the escalation button**

In the lot trace dialog, beside the existing document link column (around `index.vue:586`), add a button that routes to the new page carrying the lot already in hand:

```vue
        <el-button
          link
          type="primary"
          @click="router.push({
            path: '/inventory/lot-genealogy',
            query: { productId: String(row.productId), lotNo: row.lotNo ?? '' }
          })"
        >
          {{ $t('inventoryStocks.action.viewGenealogy') }}
        </el-button>
```

Gate it on the permission the same way the page's other permission-sensitive buttons are gated (check how this file guards buttons — likely a `v-permission` directive from `src/directives` — and follow it with `inventory:lot:genealogy`).

- [ ] **Step 2: Add the i18n key**

Add `action.viewGenealogy` to the `inventoryStocks` namespace in both locales. `inventoryStocks` lives in `operations-pages.ts`; keep zh/en parity.

- [ ] **Step 3: Run the frontend gate**

Run: `cd frontend && npm run lint && npm run type-check && npx vitest run src/i18n/`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/views/inventory/stocks/index.vue frontend/src/i18n/operations-pages.ts
git commit -m "feat: link lot trace dialog to genealogy"
```

---

## Task 16: Full verification and docs

**Files:**
- Modify: `backend/docs/未完成.md`

- [ ] **Step 1: Run the entire backend suite**

Run: `cd backend && ./mvnw test`
Expected: 0 failures, 0 errors. Record the total count; the baseline before this work was 1433 tests with 2 skipped by configuration. Do not proceed while anything is red.

- [ ] **Step 2: Run the entire frontend gate**

Run: `cd frontend && npm run lint && npm run type-check && npx vitest run && npm run check:contracts && npm run build`
Expected: all PASS.

- [ ] **Step 3: Mutation-check the two guards that matter most**

The spec names scope honesty as the worst failure mode, so prove the tests actually detect its loss:

1. Temporarily delete the `dataScopeService.applyInventoryTransactionScope(...)` call in `loadProductionCounterparts`. Run `./mvnw test -Dtest=InventoryLotGenealogyServiceTest`. `appliesDataScopeOnEveryLevelNotOnlyTheFirst` must FAIL. Restore it.
2. Temporarily change `PER_LEVEL_NODE_LIMIT` enforcement to skip adding the reason. Run the same test class. `capsFanOutPerLevelAndReportsIt` must FAIL. Restore it.

Record both outcomes. If either mutation passes, the test is decorative and must be strengthened before shipping.

- [ ] **Step 4: Update the tracker**

In `docs/未完成.md`, change item 2.8's parenthetical from `DONE（入库强制，出库自动拣选）` and drop `端到端追溯查询未扩`, since this work closes it. Add a row to the 完成记录 table dated 2026-08-18 recording: the endpoint, the page, V145, the level-batched approach and why recursive CTE and a materialized edge table were rejected, the approved security widening, the non-lot-controlled terminal reasons added during verification, the two mutation results from Step 3, and the final test counts.

- [ ] **Step 5: Commit and push**

```bash
git add backend/docs/未完成.md
git commit -m "docs: record inventory lot genealogy completion"
git push
```

---

## Self-Review

**Spec coverage:** Upstream traversal → Tasks 3, 4, 6. Downstream → Tasks 5, 6. Single read-only endpoint → Task 8. Dedicated page with search, tree, CSV recall export → Tasks 10–14. Menu node and permission by migration → Task 9. Explicit reported limits → Task 7. Counterparty resolution paths → Task 3. `bizLabel`/`documentRoute` reuse → Task 1 (as the extraction that Correction 1 requires). Escalation from the lot trace dialog → Task 15. Every acceptance criterion maps to a named test: the two-supplier and two-customer criteria to Tasks 4 and 5, empty-link root to Task 2, truncation to Task 7, `scopeLimited` to Task 7, unchanged `traceLot` to Task 1's frozen-test run, and the full-suite criterion to Task 16.

**Deliberate deviations from the spec, all recorded in the spec itself:** the three corrections in Tasks 1, 8, and 11, and the non-lot-controlled terminal reasons in Task 6. `NO_MATERIAL_ISSUED` was added to the closed set during planning — a production completion with no recorded material issue is reachable and needed a name rather than falling through to `UNKNOWN_SOURCE`; amend the spec's terminal-reason list when Task 6 lands.

**Known rough edge:** Task 4 Step 3's `orderNode` construction is written twice — once tangled, then corrected in the note beneath it. Use the note's version (`new NodeBuilder(node.key, depth)` with the owning node captured on `LinkBuilder`); the first form is wrong and is left visible only because the corrected shape is easier to understand against it.

**Type consistency:** `LotGenealogyNode`/`LotGenealogyLink` component names are fixed in Task 2 and reused verbatim by the frontend types in Task 10 and both composables. `ProductDisplay(code, name)` and `CounterpartyIndex.supplierFor`/`customerFor` are defined in Tasks 2–3 and consumed unchanged afterwards. The permission string `inventory:lot:genealogy` appears in Tasks 8, 9, 14, and 15 and is identical in all four.
