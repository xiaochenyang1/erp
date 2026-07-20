# ERP Purchase Receipt And Inventory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有采购订单模块基础上补齐采购入库最小闭环，支持采购入库草稿新增、详情、分页、编辑、作废、正式过账，并在过账时同步回写采购订单收货进度、库存余额和库存流水。

**Architecture:** 采购入库业务落在 `purchase/receipt` 包下，继续沿用 `controller + service + mapper + model + web` 分层；库存余额与库存流水先落在 `inventory/stock` 包下，作为采购入库过账事务内同步持久化的对象。采购订单继续保留审批准入职责，采购入库不新增独立审批流，过账时统一校验采购订单审批状态和剩余可入库数量。

**Tech Stack:** Spring Boot 3.3.x, MyBatis-Plus, Flyway, H2, MockMvc, JUnit 5

---

## File Map

**Create:**
- `src/main/resources/db/migration/V14__purchase_receipt_inventory_schema.sql`
- `src/main/java/com/tuowei/erp/purchase/receipt/controller/PurchaseReceiptController.java`
- `src/main/java/com/tuowei/erp/purchase/receipt/service/PurchaseReceiptService.java`
- `src/main/java/com/tuowei/erp/purchase/receipt/service/PurchaseReceiptNumberService.java`
- `src/main/java/com/tuowei/erp/purchase/receipt/mapper/PurchaseReceiptMapper.java`
- `src/main/java/com/tuowei/erp/purchase/receipt/mapper/PurchaseReceiptLineMapper.java`
- `src/main/java/com/tuowei/erp/purchase/receipt/model/PurchaseReceiptEntity.java`
- `src/main/java/com/tuowei/erp/purchase/receipt/model/PurchaseReceiptLineEntity.java`
- `src/main/java/com/tuowei/erp/purchase/receipt/web/PurchaseReceiptCreateRequest.java`
- `src/main/java/com/tuowei/erp/purchase/receipt/web/PurchaseReceiptUpdateRequest.java`
- `src/main/java/com/tuowei/erp/purchase/receipt/web/PurchaseReceiptPageQuery.java`
- `src/main/java/com/tuowei/erp/purchase/receipt/web/PurchaseReceiptLineRequest.java`
- `src/main/java/com/tuowei/erp/purchase/receipt/web/PurchaseReceiptLineResponse.java`
- `src/main/java/com/tuowei/erp/purchase/receipt/web/PurchaseReceiptResponse.java`
- `src/main/java/com/tuowei/erp/inventory/stock/mapper/InventoryBalanceMapper.java`
- `src/main/java/com/tuowei/erp/inventory/stock/mapper/InventoryTransactionMapper.java`
- `src/main/java/com/tuowei/erp/inventory/stock/model/InventoryBalanceEntity.java`
- `src/main/java/com/tuowei/erp/inventory/stock/model/InventoryTransactionEntity.java`
- `src/test/java/com/tuowei/erp/purchase/receipt/PurchaseReceiptSchemaMigrationTest.java`
- `src/test/java/com/tuowei/erp/purchase/receipt/PurchaseReceiptControllerCreateDetailTest.java`
- `src/test/java/com/tuowei/erp/purchase/receipt/PurchaseReceiptControllerPageAndUpdateTest.java`
- `src/test/java/com/tuowei/erp/purchase/receipt/PurchaseReceiptControllerPostTest.java`

**Modify:**
- `src/main/java/com/tuowei/erp/purchase/order/model/PurchaseOrderEntity.java`
- `src/main/java/com/tuowei/erp/purchase/order/model/PurchaseOrderLineEntity.java`
- `src/main/java/com/tuowei/erp/purchase/order/service/PurchaseOrderService.java`
- `src/main/java/com/tuowei/erp/purchase/order/web/PurchaseOrderResponse.java`
- `src/main/java/com/tuowei/erp/purchase/order/web/PurchaseOrderLineResponse.java`
- `src/main/java/com/tuowei/erp/common/exception/GlobalExceptionHandler.java`
- `db/init/02_create_tables.sql`
- `db/init/03_create_indexes.sql`
- `db/init/04_init_dict_and_config.sql`
- `db/init/05_init_security.sql`
- `src/test/java/com/tuowei/erp/db/DbScriptLayoutTest.java`
- `src/test/java/com/tuowei/erp/common/security/SecurityConfigTest.java`
- `src/test/java/com/tuowei/erp/system/controller/HealthControllerTest.java`

## Task 1: Add Purchase Receipt And Inventory Schema

**Files:**
- Create: `src/main/resources/db/migration/V14__purchase_receipt_inventory_schema.sql`
- Create: `src/test/java/com/tuowei/erp/purchase/receipt/PurchaseReceiptSchemaMigrationTest.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/order/model/PurchaseOrderEntity.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/order/model/PurchaseOrderLineEntity.java`

- [x] **Step 1: Write the failing migration test**

```java
@Test
void flywayCreatesPurchaseReceiptAndInventoryTables() {
    assertThat(countTables("pur_receipt")).isEqualTo(1);
    assertThat(countTables("pur_receipt_line")).isEqualTo(1);
    assertThat(countTables("inv_balance")).isEqualTo(1);
    assertThat(countTables("inv_txn")).isEqualTo(1);
    assertThat(countColumns("pur_order", "receipt_status")).isEqualTo(1);
    assertThat(countColumns("pur_order_line", "received_qty")).isEqualTo(1);
    assertThat(countIndexes("uk_pur_receipt_receipt_no")).isEqualTo(1);
    assertThat(countIndexes("uk_inv_balance_warehouse_id_product_id")).isEqualTo(1);
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=PurchaseReceiptSchemaMigrationTest" test
```

Expected:

```text
FAIL ... expected: 1 but was: 0
```

- [x] **Step 3: Add `V14` schema and purchase-order receipt fields**

```sql
ALTER TABLE pur_order
    ADD COLUMN receipt_status VARCHAR(32) NOT NULL DEFAULT 'NOT_RECEIVED';

ALTER TABLE pur_order_line
    ADD COLUMN received_qty DECIMAL(18, 4) NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS pur_receipt (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    receipt_no VARCHAR(64) NOT NULL,
    order_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    receipt_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    total_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    total_tax_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);
```

- [x] **Step 4: Re-run the same test and verify it passes**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=PurchaseReceiptSchemaMigrationTest" test
```

Expected:

```text
BUILD SUCCESS
```

Rules:
- `pur_receipt.receipt_no` 唯一索引名固定为 `uk_pur_receipt_receipt_no`
- `inv_balance` 唯一索引名固定为 `uk_inv_balance_warehouse_id_product_id`
- `inv_txn` 必须保留 `biz_type`、`biz_no`、`biz_line_id`、`direction`
- `pur_order.receipt_status` 默认值为 `NOT_RECEIVED`
- `pur_order_line.received_qty` 默认值为 `0`

## Task 2: Implement Purchase Receipt Draft Create And Detail APIs

**Files:**
- Create: `src/main/java/com/tuowei/erp/purchase/receipt/controller/PurchaseReceiptController.java`
- Create: `src/main/java/com/tuowei/erp/purchase/receipt/service/PurchaseReceiptService.java`
- Create: `src/main/java/com/tuowei/erp/purchase/receipt/service/PurchaseReceiptNumberService.java`
- Create: `src/main/java/com/tuowei/erp/purchase/receipt/mapper/PurchaseReceiptMapper.java`
- Create: `src/main/java/com/tuowei/erp/purchase/receipt/mapper/PurchaseReceiptLineMapper.java`
- Create: `src/main/java/com/tuowei/erp/purchase/receipt/model/PurchaseReceiptEntity.java`
- Create: `src/main/java/com/tuowei/erp/purchase/receipt/model/PurchaseReceiptLineEntity.java`
- Create: `src/main/java/com/tuowei/erp/purchase/receipt/web/PurchaseReceiptCreateRequest.java`
- Create: `src/main/java/com/tuowei/erp/purchase/receipt/web/PurchaseReceiptLineRequest.java`
- Create: `src/main/java/com/tuowei/erp/purchase/receipt/web/PurchaseReceiptLineResponse.java`
- Create: `src/main/java/com/tuowei/erp/purchase/receipt/web/PurchaseReceiptResponse.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/order/service/PurchaseOrderService.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/order/web/PurchaseOrderResponse.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/order/web/PurchaseOrderLineResponse.java`
- Create: `src/test/java/com/tuowei/erp/purchase/receipt/PurchaseReceiptControllerCreateDetailTest.java`

- [x] **Step 1: Write the failing integration test for draft creation, totals calculation and detail query**

```java
@Test
@WithMockUser(username = "admin")
void createsReceiptDraftAndQueriesDetail() throws Exception {
    long orderId = seedApprovedPurchaseOrder();
    long warehouseId = seedActiveWarehouse();

    MvcResult result = mockMvc.perform(post("/api/purchase/receipts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "orderId": %d,
                              "warehouseId": %d,
                              "receiptDate": "2026-04-30",
                              "remark": "首批到货",
                              "lines": [
                                { "orderLineId": %d, "qty": 3.0000, "remark": "先到三件" }
                              ]
                            }
                            """.formatted(orderId, warehouseId, orderLineId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.totalQuantity").value(3.0))
            .andReturn();

    mockMvc.perform(get("/api/purchase/receipts/{id}", readId(result)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderId").value(orderId))
            .andExpect(jsonPath("$.data.warehouseId").value(warehouseId));
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=PurchaseReceiptControllerCreateDetailTest" test
```

Expected:

```text
FAIL ... No static resource api/purchase/receipts
```

- [x] **Step 3: Implement minimal draft create/detail flow**

```java
@PostMapping
public ApiResponse<PurchaseReceiptResponse> create(@Valid @RequestBody PurchaseReceiptCreateRequest request) {
    return ApiResponse.success(purchaseReceiptService.create(request));
}

@GetMapping("/{id}")
public ApiResponse<PurchaseReceiptResponse> detail(@PathVariable Long id) {
    return ApiResponse.success(purchaseReceiptService.getById(id));
}
```

```java
if (!"APPROVED".equals(order.getStatus())) {
    throw new IllegalArgumentException("采购订单未审批通过，不能创建采购入库单");
}
```

- [x] **Step 4: Re-run the same test and verify it passes**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=PurchaseReceiptControllerCreateDetailTest" test
```

Expected:

```text
BUILD SUCCESS
```

Rules:
- 客户端不允许传 `price`、`taxRate`、`amount`、`taxAmount`
- 服务端必须按采购订单明细带出 `productId`、`price`、`taxRate`
- 新建草稿默认 `status = DRAFT`
- 单号规则使用 `PURCHASE_RECEIPT`，格式建议 `PR202604300001`

## Task 3: Implement Purchase Receipt Page, Draft Update And Cancel APIs

**Files:**
- Create: `src/main/java/com/tuowei/erp/purchase/receipt/web/PurchaseReceiptPageQuery.java`
- Create: `src/main/java/com/tuowei/erp/purchase/receipt/web/PurchaseReceiptUpdateRequest.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/receipt/service/PurchaseReceiptService.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/receipt/controller/PurchaseReceiptController.java`
- Create: `src/test/java/com/tuowei/erp/purchase/receipt/PurchaseReceiptControllerPageAndUpdateTest.java`

- [x] **Step 1: Write the failing test for page query, draft update and draft cancel**

```java
mockMvc.perform(get("/api/purchase/receipts")
                .param("status", "DRAFT")
                .param("warehouseId", String.valueOf(warehouseId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));

mockMvc.perform(put("/api/purchase/receipts/{id}", receiptId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatedJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalQuantity").value(5.0));

mockMvc.perform(post("/api/purchase/receipts/{id}/cancel", receiptId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CANCELLED"));
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=PurchaseReceiptControllerPageAndUpdateTest" test
```

Expected:

```text
FAIL ... Request method 'GET' is not supported
```

- [x] **Step 3: Implement list, update and cancel**

```java
if (!"DRAFT".equals(entity.getStatus())) {
    throw new IllegalArgumentException("当前采购入库单状态不允许编辑");
}

if (!"DRAFT".equals(entity.getStatus())) {
    throw new IllegalArgumentException("当前采购入库单状态不允许作废");
}
```

```java
@GetMapping
public ApiResponse<PageResponse<PurchaseReceiptResponse>> list(PurchaseReceiptPageQuery query) {
    return ApiResponse.success(purchaseReceiptService.list(query));
}
```

- [x] **Step 4: Re-run the same test and verify it passes**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=PurchaseReceiptControllerPageAndUpdateTest" test
```

Expected:

```text
BUILD SUCCESS
```

Rules:
- `PUT` 和 `cancel` 仅允许 `DRAFT`
- 更新时整单覆盖明细
- 分页支持 `keyword`、`orderId`、`warehouseId`、`status`、`receiptDateFrom`、`receiptDateTo`
- 列表按 `id DESC`

## Task 4: Implement Receipt Post And Inventory Writeback

**Files:**
- Create: `src/main/java/com/tuowei/erp/inventory/stock/mapper/InventoryBalanceMapper.java`
- Create: `src/main/java/com/tuowei/erp/inventory/stock/mapper/InventoryTransactionMapper.java`
- Create: `src/main/java/com/tuowei/erp/inventory/stock/model/InventoryBalanceEntity.java`
- Create: `src/main/java/com/tuowei/erp/inventory/stock/model/InventoryTransactionEntity.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/receipt/service/PurchaseReceiptService.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/receipt/controller/PurchaseReceiptController.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/order/service/PurchaseOrderService.java`
- Create: `src/test/java/com/tuowei/erp/purchase/receipt/PurchaseReceiptControllerPostTest.java`

- [x] **Step 1: Write the failing post test for inventory writeback and over-receipt rejection**

```java
mockMvc.perform(post("/api/purchase/receipts/{id}/post", receiptId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("POSTED"));

assertThat(queryReceivedQty(orderLineId)).isEqualByComparingTo("3.0000");
assertThat(queryReceiptStatus(orderId)).isEqualTo("PARTIAL_RECEIVED");
assertThat(queryBalanceQty(warehouseId, productId)).isEqualByComparingTo("3.0000");
assertThat(queryTxnCount("PURCHASE_RECEIPT", receiptNo)).isEqualTo(1);
```

```java
mockMvc.perform(post("/api/purchase/receipts/{id}/post", overReceiptId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("入库数量超过采购订单剩余可入库数量"));
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=PurchaseReceiptControllerPostTest" test
```

Expected:

```text
FAIL ... No static resource api/purchase/receipts/{id}/post
```

- [x] **Step 3: Implement posting transaction and inventory persistence**

```java
@PostMapping("/{id}/post")
public ApiResponse<PurchaseReceiptResponse> post(@PathVariable Long id) {
    return ApiResponse.success(purchaseReceiptService.post(id));
}
```

```java
if (requestQty.compareTo(remainingQty) > 0) {
    throw new IllegalArgumentException("入库数量超过采购订单剩余可入库数量");
}

receipt.setStatus("POSTED");
orderLine.setReceivedQty(orderLine.getReceivedQty().add(receiptLine.getQty()));
balance.setQtyOnHand(balance.getQtyOnHand().add(receiptLine.getQty()));
transaction.setBizType("PURCHASE_RECEIPT");
transaction.setDirection("IN");
```

- [x] **Step 4: Re-run the same test and verify it passes**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=PurchaseReceiptControllerPostTest" test
```

Expected:

```text
BUILD SUCCESS
```

Rules:
- `post` 仅允许 `DRAFT`
- 采购订单必须是 `APPROVED`
- 重复过账要拦截
- `received_qty` 累加后回写 `receipt_status`
- `inv_txn` 只追加，不更新历史记录

## Task 5: Align Initialization Scripts And Run Regression

**Files:**
- Modify: `db/init/02_create_tables.sql`
- Modify: `db/init/03_create_indexes.sql`
- Modify: `db/init/04_init_dict_and_config.sql`
- Modify: `db/init/05_init_security.sql`
- Modify: `src/main/java/com/tuowei/erp/common/exception/GlobalExceptionHandler.java`
- Modify: `src/test/java/com/tuowei/erp/db/DbScriptLayoutTest.java`
- Modify: `src/test/java/com/tuowei/erp/common/security/SecurityConfigTest.java`
- Modify: `src/test/java/com/tuowei/erp/system/controller/HealthControllerTest.java`

- [x] **Step 1: Add failing script assertions for receipt, balance and transaction artifacts**

```java
assertThat(createTablesScript).contains("CREATE TABLE IF NOT EXISTS pur_receipt");
assertThat(createTablesScript).contains("CREATE TABLE IF NOT EXISTS inv_balance");
assertThat(createIndexesScript).contains("uk_pur_receipt_receipt_no");
assertThat(createIndexesScript).contains("uk_inv_balance_warehouse_id_product_id");
assertThat(dictAndConfigScript).contains("PURCHASE_RECEIPT");
assertThat(securityScript).contains("PR202604300001");
```

- [x] **Step 2: Run `DbScriptLayoutTest` to verify it fails before alignment**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=DbScriptLayoutTest" test
```

Expected:

```text
FAIL ... to contain "CREATE TABLE IF NOT EXISTS pur_receipt"
```

- [x] **Step 3: Update scripts, duplicate-key messages and slice-test mocks**

```sql
INSERT INTO sys_sequence_rule (id, biz_type, prefix, date_pattern, seq_length, current_value)
VALUES (2004, 'PURCHASE_RECEIPT', 'PR', 'yyyyMMdd', 4, 1)
ON DUPLICATE KEY UPDATE current_value = VALUES(current_value);
```

```java
if (message.contains("uk_pur_receipt_receipt_no")) {
    return "采购入库单号已存在";
}
```

- [x] **Step 4: Re-run targeted script and slice tests**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=DbScriptLayoutTest,SecurityConfigTest,HealthControllerTest" test
```

Expected:

```text
BUILD SUCCESS
```

- [x] **Step 5: Run full regression**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" test
```

Expected:

```text
BUILD SUCCESS
```

Rules:
- 初始化脚本必须有已审批采购订单、演示采购入库、演示库存余额、演示库存流水
- `PURCHASE_RECEIPT` 编号规则要和演示入库单号对齐，别把第一张正式单直接撞号
- 新增 `InventoryBalanceMapper`、`InventoryTransactionMapper` 后，要同步补齐 `@WebMvcTest` 的 mock

## Self-Review

- 规格覆盖：本计划覆盖了采购入库单表、库存余额、库存流水、采购订单回写、初始化脚本和全量回归，没有遗漏设计文档中的主链路要求
- 占位检查：全文没有待补充标记、空白步骤或“参照上一任务”这类偷懒写法
- 类型一致性：采购入库状态统一使用 `DRAFT / POSTED / CANCELLED`；采购订单收货状态统一使用 `NOT_RECEIVED / PARTIAL_RECEIVED / RECEIVED`；库存流水业务类型统一使用 `PURCHASE_RECEIPT`

