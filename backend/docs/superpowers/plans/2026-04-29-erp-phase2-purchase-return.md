# ERP Purchase Return Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有采购订单、采购入库和库存模块基础上补齐采购退货最小闭环，支持采购退货草稿新增、详情、分页、编辑、作废、正式过账，并在过账时同步冲减库存、回写采购入库明细累计退货数量以及采购订单净收货进度。

**Architecture:** 采购退货业务落在 `purchase/returnorder` 包下，继续沿用 `controller + service + mapper + model + web` 分层；退货单头只允许关联一张已过账采购入库单，退货明细只允许引用该入库单下的明细行，并由服务端继承原入库单价和税率。正式过账时保持同步事务，一次性更新 `pur_return`、`pur_receipt_line.returned_qty`、`pur_order_line.received_qty`、`pur_order.receipt_status`、`inv_balance` 和 `inv_txn`，任何一步失败都整单回滚。

**Tech Stack:** Spring Boot 3.3.x, MyBatis-Plus, Flyway, H2, MockMvc, JUnit 5

---

## File Map

**Create:**
- `src/main/resources/db/migration/V15__purchase_return_schema.sql`
- `src/main/java/com/tuowei/erp/purchase/returnorder/controller/PurchaseReturnController.java`
- `src/main/java/com/tuowei/erp/purchase/returnorder/service/PurchaseReturnService.java`
- `src/main/java/com/tuowei/erp/purchase/returnorder/service/PurchaseReturnNumberService.java`
- `src/main/java/com/tuowei/erp/purchase/returnorder/mapper/PurchaseReturnMapper.java`
- `src/main/java/com/tuowei/erp/purchase/returnorder/mapper/PurchaseReturnLineMapper.java`
- `src/main/java/com/tuowei/erp/purchase/returnorder/model/PurchaseReturnEntity.java`
- `src/main/java/com/tuowei/erp/purchase/returnorder/model/PurchaseReturnLineEntity.java`
- `src/main/java/com/tuowei/erp/purchase/returnorder/web/PurchaseReturnCreateRequest.java`
- `src/main/java/com/tuowei/erp/purchase/returnorder/web/PurchaseReturnUpdateRequest.java`
- `src/main/java/com/tuowei/erp/purchase/returnorder/web/PurchaseReturnPageQuery.java`
- `src/main/java/com/tuowei/erp/purchase/returnorder/web/PurchaseReturnLineRequest.java`
- `src/main/java/com/tuowei/erp/purchase/returnorder/web/PurchaseReturnLineResponse.java`
- `src/main/java/com/tuowei/erp/purchase/returnorder/web/PurchaseReturnResponse.java`
- `src/test/java/com/tuowei/erp/purchase/returnorder/PurchaseReturnSchemaMigrationTest.java`
- `src/test/java/com/tuowei/erp/purchase/returnorder/PurchaseReturnControllerCreateDetailTest.java`
- `src/test/java/com/tuowei/erp/purchase/returnorder/PurchaseReturnControllerPageAndUpdateTest.java`
- `src/test/java/com/tuowei/erp/purchase/returnorder/PurchaseReturnControllerPostTest.java`

**Modify:**
- `src/main/java/com/tuowei/erp/purchase/receipt/model/PurchaseReceiptLineEntity.java`
- `src/main/java/com/tuowei/erp/common/exception/GlobalExceptionHandler.java`
- `db/init/02_create_tables.sql`
- `db/init/03_create_indexes.sql`
- `db/init/04_init_dict_and_config.sql`
- `db/init/05_init_security.sql`
- `src/test/java/com/tuowei/erp/db/DbScriptLayoutTest.java`
- `src/test/java/com/tuowei/erp/common/security/SecurityConfigTest.java`
- `src/test/java/com/tuowei/erp/system/controller/HealthControllerTest.java`

## Task 1: Add Purchase Return Schema

**Files:**
- Create: `src/main/resources/db/migration/V15__purchase_return_schema.sql`
- Create: `src/test/java/com/tuowei/erp/purchase/returnorder/PurchaseReturnSchemaMigrationTest.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/receipt/model/PurchaseReceiptLineEntity.java`

- [x] **Step 1: Write the failing migration test**

```java
@Test
void flywayCreatesPurchaseReturnTablesAndReceiptReturnedQtyColumn() {
    assertThat(countTables("pur_return")).isEqualTo(1);
    assertThat(countTables("pur_return_line")).isEqualTo(1);
    assertThat(countColumns("pur_receipt_line", "returned_qty")).isEqualTo(1);
    assertThat(countColumns("pur_return", "receipt_id")).isEqualTo(1);
    assertThat(countColumns("pur_return", "warehouse_id")).isEqualTo(1);
    assertThat(countColumns("pur_return_line", "receipt_line_id")).isEqualTo(1);
    assertThat(countColumns("pur_return_line", "price")).isEqualTo(1);
    assertThat(countColumns("pur_return_line", "tax_rate")).isEqualTo(1);
    assertThat(countColumns("pur_return_line", "amount")).isEqualTo(1);
    assertThat(countColumns("pur_return_line", "tax_amount")).isEqualTo(1);
    assertThat(countColumns("pur_return_line", "warehouse_id")).isEqualTo(0);
    assertThat(countIndexes("uk_pur_return_return_no")).isEqualTo(1);
    assertThat(countIndexes("idx_pur_return_receipt_id")).isEqualTo(1);
    assertThat(countIndexes("idx_pur_return_line_return_id")).isEqualTo(1);
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=PurchaseReturnSchemaMigrationTest" test
```

Expected:

```text
FAIL ... expected: 1 but was: 0
```

- [x] **Step 3: Add `V15` migration and map `returnedQty` on receipt lines**

```sql
ALTER TABLE pur_receipt_line
    ADD COLUMN IF NOT EXISTS returned_qty DECIMAL(18, 4) NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS pur_return (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    return_no VARCHAR(64) NOT NULL,
    receipt_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    return_date DATE NOT NULL,
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

CREATE TABLE IF NOT EXISTS pur_return_line (
    id BIGINT PRIMARY KEY,
    return_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    receipt_line_id BIGINT NOT NULL,
    order_line_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    qty DECIMAL(18, 4) NOT NULL,
    price DECIMAL(18, 2) NOT NULL,
    tax_rate DECIMAL(8, 4) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    tax_amount DECIMAL(18, 2) NOT NULL,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_pur_return_return_no ON pur_return (return_no);
CREATE INDEX IF NOT EXISTS idx_pur_return_receipt_id ON pur_return (receipt_id);
CREATE INDEX IF NOT EXISTS idx_pur_return_line_return_id ON pur_return_line (return_id);
CREATE INDEX IF NOT EXISTS idx_pur_return_line_receipt_line_id ON pur_return_line (receipt_line_id);
```

```java
private BigDecimal returnedQty;

public BigDecimal getReturnedQty() {
    return returnedQty;
}

public void setReturnedQty(BigDecimal returnedQty) {
    this.returnedQty = returnedQty;
}
```

- [x] **Step 4: Re-run the same test and verify it passes**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=PurchaseReturnSchemaMigrationTest" test
```

Expected:

```text
BUILD SUCCESS
```

Rules:
- `pur_return.status` 只允许 `DRAFT / POSTED / CANCELLED`
- `pur_return.return_no` 唯一索引名固定为 `uk_pur_return_return_no`
- `pur_receipt_line.returned_qty` 默认值固定为 `0`
- `pur_return_line` 必须保留 `receipt_line_id`、`order_line_id`、`price`、`tax_rate`、`amount`、`tax_amount`

## Task 2: Implement Purchase Return Draft Create And Detail APIs

**Files:**
- Create: `src/main/java/com/tuowei/erp/purchase/returnorder/controller/PurchaseReturnController.java`
- Create: `src/main/java/com/tuowei/erp/purchase/returnorder/service/PurchaseReturnService.java`
- Create: `src/main/java/com/tuowei/erp/purchase/returnorder/service/PurchaseReturnNumberService.java`
- Create: `src/main/java/com/tuowei/erp/purchase/returnorder/mapper/PurchaseReturnMapper.java`
- Create: `src/main/java/com/tuowei/erp/purchase/returnorder/mapper/PurchaseReturnLineMapper.java`
- Create: `src/main/java/com/tuowei/erp/purchase/returnorder/model/PurchaseReturnEntity.java`
- Create: `src/main/java/com/tuowei/erp/purchase/returnorder/model/PurchaseReturnLineEntity.java`
- Create: `src/main/java/com/tuowei/erp/purchase/returnorder/web/PurchaseReturnCreateRequest.java`
- Create: `src/main/java/com/tuowei/erp/purchase/returnorder/web/PurchaseReturnLineRequest.java`
- Create: `src/main/java/com/tuowei/erp/purchase/returnorder/web/PurchaseReturnLineResponse.java`
- Create: `src/main/java/com/tuowei/erp/purchase/returnorder/web/PurchaseReturnResponse.java`
- Create: `src/test/java/com/tuowei/erp/purchase/returnorder/PurchaseReturnControllerCreateDetailTest.java`

- [x] **Step 1: Write the failing integration test for draft creation, inherited pricing and detail query**

```java
@Test
@WithMockUser(username = "admin")
void createsReturnDraftAndQueriesDetail() throws Exception {
    long receiptId = seedPostedPurchaseReceipt();

    MvcResult result = mockMvc.perform(post("/api/purchase/returns")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "receiptId": %d,
                              "returnDate": "2026-05-01",
                              "remark": "首批退货",
                              "lines": [
                                { "receiptLineId": %d, "qty": 1.0000, "remark": "包装破损退回" }
                              ]
                            }
                            """.formatted(receiptId, receiptLineId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.returnNo").value("PRT202605010001"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.receiptId").value(receiptId))
            .andExpect(jsonPath("$.data.receiptNo").value("PR202604300001"))
            .andExpect(jsonPath("$.data.totalQuantity").value(1.0))
            .andExpect(jsonPath("$.data.totalAmount").value(100.0))
            .andExpect(jsonPath("$.data.totalTaxAmount").value(13.0))
            .andExpect(jsonPath("$.data.lines[0].receiptLineId").value(receiptLineId))
            .andExpect(jsonPath("$.data.lines[0].productId").value(productId))
            .andExpect(jsonPath("$.data.lines[0].price").value(100.0))
            .andExpect(jsonPath("$.data.lines[0].taxRate").value(13.0))
            .andExpect(jsonPath("$.data.lines[0].receiptQty").value(3.0))
            .andExpect(jsonPath("$.data.lines[0].returnedQty").value(0.0))
            .andExpect(jsonPath("$.data.lines[0].availableReturnQty").value(3.0))
            .andReturn();

    mockMvc.perform(get("/api/purchase/returns/{id}", readId(result)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderNo").value("PO202604280001"))
            .andExpect(jsonPath("$.data.warehouseName").value("默认主仓"))
            .andExpect(jsonPath("$.data.lines[0].productName").value("演示商品A"));
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=PurchaseReturnControllerCreateDetailTest" test
```

Expected:

```text
FAIL ... No static resource api/purchase/returns
```

- [x] **Step 3: Implement minimal draft create/detail flow**

```java
@PostMapping
public ApiResponse<PurchaseReturnResponse> create(@Valid @RequestBody PurchaseReturnCreateRequest request) {
    return ApiResponse.success(purchaseReturnService.create(request));
}

@GetMapping("/{id}")
public ApiResponse<PurchaseReturnResponse> detail(@PathVariable Long id) {
    return ApiResponse.success(purchaseReturnService.getById(id));
}
```

```java
if (!"POSTED".equals(receipt.getStatus())) {
    throw new IllegalArgumentException("采购入库单未过账，不能创建采购退货单");
}

PurchaseReturnLineEntity returnLine = new PurchaseReturnLineEntity();
returnLine.setReceiptLineId(receiptLine.getId());
returnLine.setOrderLineId(receiptLine.getOrderLineId());
returnLine.setProductId(receiptLine.getProductId());
returnLine.setQty(scaleQuantity(lineRequest.qty()));
returnLine.setPrice(scaleAmount(receiptLine.getPrice()));
returnLine.setTaxRate(scaleRate(receiptLine.getTaxRate()));
returnLine.setAmount(scaleAmount(returnLine.getQty().multiply(returnLine.getPrice())));
returnLine.setTaxAmount(scaleAmount(
        returnLine.getAmount().multiply(returnLine.getTaxRate()).divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP)
));
```

```java
public String nextReturnNo(LocalDate bizDate) {
    SequenceRuleEntity rule = sequenceRuleMapper.selectOne(new LambdaQueryWrapper<SequenceRuleEntity>()
            .eq(SequenceRuleEntity::getBizType, "PURCHASE_RETURN"));
    if (rule == null) {
        throw new IllegalArgumentException("采购退货单编号规则不存在");
    }
    if (!"ACTIVE".equalsIgnoreCase(rule.getStatus())) {
        throw new IllegalArgumentException("采购退货单编号规则已停用");
    }
    long nextValue = (rule.getCurrentValue() == null ? 0L : rule.getCurrentValue()) + 1L;
    rule.setCurrentValue(nextValue);
    sequenceRuleMapper.updateById(rule);
    return rule.getPrefix()
            + bizDate.format(DateTimeFormatter.ofPattern(rule.getDatePattern()))
            + String.format("%0" + rule.getSeqLength() + "d", nextValue);
}
```

- [x] **Step 4: Re-run the same test and verify it passes**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=PurchaseReturnControllerCreateDetailTest" test
```

Expected:

```text
BUILD SUCCESS
```

Rules:
- 客户端不允许传 `productId`、`orderLineId`、`price`、`taxRate`、`amount`、`taxAmount`
- 创建草稿时只能引用 `POSTED` 的采购入库单
- 每条退货明细都必须属于头上的 `receiptId`
- 单号规则使用 `PURCHASE_RETURN`，格式固定为 `PRTyyyyMMdd####`

## Task 3: Implement Purchase Return Page, Draft Update And Cancel APIs

**Files:**
- Create: `src/main/java/com/tuowei/erp/purchase/returnorder/web/PurchaseReturnPageQuery.java`
- Create: `src/main/java/com/tuowei/erp/purchase/returnorder/web/PurchaseReturnUpdateRequest.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/returnorder/service/PurchaseReturnService.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/returnorder/controller/PurchaseReturnController.java`
- Create: `src/test/java/com/tuowei/erp/purchase/returnorder/PurchaseReturnControllerPageAndUpdateTest.java`

- [x] **Step 1: Write the failing test for page query, draft update and draft cancel**

```java
mockMvc.perform(get("/api/purchase/returns")
                .param("status", "DRAFT")
                .param("receiptId", String.valueOf(receiptId))
                .param("warehouseId", String.valueOf(warehouseId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.records[0].returnNo").value("PRT202605010001"));

mockMvc.perform(put("/api/purchase/returns/{id}", returnId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatedJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalQuantity").value(2.0))
        .andExpect(jsonPath("$.data.totalAmount").value(200.0))
        .andExpect(jsonPath("$.data.lines[0].availableReturnQty").value(3.0));

mockMvc.perform(post("/api/purchase/returns/{id}/cancel", returnId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CANCELLED"));
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=PurchaseReturnControllerPageAndUpdateTest" test
```

Expected:

```text
FAIL ... Request method 'GET' is not supported
```

- [x] **Step 3: Implement list, update and cancel**

```java
@GetMapping
public ApiResponse<PageResponse<PurchaseReturnResponse>> list(PurchaseReturnPageQuery query) {
    return ApiResponse.success(purchaseReturnService.list(query));
}

@PutMapping("/{id}")
public ApiResponse<PurchaseReturnResponse> update(@PathVariable Long id, @Valid @RequestBody PurchaseReturnUpdateRequest request) {
    return ApiResponse.success(purchaseReturnService.update(id, request));
}

@PostMapping("/{id}/cancel")
public ApiResponse<PurchaseReturnResponse> cancel(@PathVariable Long id) {
    return ApiResponse.success(purchaseReturnService.cancel(id));
}
```

```java
if (!"DRAFT".equals(entity.getStatus())) {
    throw new IllegalArgumentException("当前采购退货单状态不允许编辑");
}

if (!"DRAFT".equals(entity.getStatus())) {
    throw new IllegalArgumentException("当前采购退货单状态不允许作废");
}

LambdaQueryWrapper<PurchaseReturnEntity> wrapper = new LambdaQueryWrapper<PurchaseReturnEntity>()
        .eq(PurchaseReturnEntity::getDeletedFlag, 0)
        .eq(query.getReceiptId() != null, PurchaseReturnEntity::getReceiptId, query.getReceiptId())
        .eq(query.getWarehouseId() != null, PurchaseReturnEntity::getWarehouseId, query.getWarehouseId())
        .eq(StringUtils.hasText(status), PurchaseReturnEntity::getStatus, status)
        .orderByDesc(PurchaseReturnEntity::getId);
```

- [x] **Step 4: Re-run the same test and verify it passes**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=PurchaseReturnControllerPageAndUpdateTest" test
```

Expected:

```text
BUILD SUCCESS
```

Rules:
- `PUT` 和 `cancel` 仅允许 `DRAFT`
- 更新时整单覆盖明细，但不允许换 `receiptId`
- 分页支持 `keyword`、`receiptId`、`warehouseId`、`status`、`returnDateFrom`、`returnDateTo`
- 列表按 `id DESC`

## Task 4: Implement Return Post And Inventory / Order Writeback

**Files:**
- Modify: `src/main/java/com/tuowei/erp/purchase/returnorder/service/PurchaseReturnService.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/returnorder/controller/PurchaseReturnController.java`
- Create: `src/test/java/com/tuowei/erp/purchase/returnorder/PurchaseReturnControllerPostTest.java`

- [x] **Step 1: Write the failing post test for inventory deduction, order writeback and rejection cases**

```java
mockMvc.perform(post("/api/purchase/returns/{id}/post", returnId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("POSTED"));

assertThat(queryReceiptLineReturnedQty(receiptLineId)).isEqualByComparingTo("1.0000");
assertThat(queryOrderLineReceivedQty(orderLineId)).isEqualByComparingTo("2.0000");
assertThat(queryOrderReceiptStatus(orderId)).isEqualTo("PARTIAL_RECEIVED");
assertThat(queryBalanceQty(warehouseId, productId)).isEqualByComparingTo("2.0000");
assertThat(queryBalanceAmount(warehouseId, productId)).isEqualByComparingTo("200.00");
assertThat(queryTxnCount("PURCHASE_RETURN", "PRT202605010001")).isEqualTo(1);
assertThat(queryTxnDirection("PURCHASE_RETURN", "PRT202605010001")).isEqualTo("OUT");
```

```java
mockMvc.perform(post("/api/purchase/returns/{id}/post", overReturnId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("退货数量超过采购入库明细剩余可退数量"));

mockMvc.perform(post("/api/purchase/returns/{id}/post", insufficientInventoryReturnId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("库存不足，不能执行采购退货"));
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=PurchaseReturnControllerPostTest" test
```

Expected:

```text
FAIL ... No static resource api/purchase/returns/{id}/post
```

- [x] **Step 3: Implement posting transaction and all writebacks**

```java
@PostMapping("/{id}/post")
public ApiResponse<PurchaseReturnResponse> post(@PathVariable Long id) {
    return ApiResponse.success(purchaseReturnService.post(id));
}
```

```java
BigDecimal receiptQty = scaleQuantity(receiptLine.getQty());
BigDecimal returnedQty = safeQuantity(receiptLine.getReturnedQty());
BigDecimal availableReturnQty = receiptQty.subtract(returnedQty);
BigDecimal requestQty = scaleQuantity(returnLine.getQty());
if (requestQty.compareTo(availableReturnQty) > 0) {
    throw new IllegalArgumentException("退货数量超过采购入库明细剩余可退数量");
}

InventoryBalanceEntity balance = requireInventoryBalance(returnEntity.getWarehouseId(), returnLine.getProductId());
if (safeQuantity(balance.getQtyOnHand()).compareTo(requestQty) < 0) {
    throw new IllegalArgumentException("库存不足，不能执行采购退货");
}

receiptLine.setReturnedQty(scaleQuantity(returnedQty.add(requestQty)));
purchaseReceiptLineMapper.updateById(receiptLine);

orderLine.setReceivedQty(scaleQuantity(safeQuantity(orderLine.getReceivedQty()).subtract(requestQty)));
purchaseOrderLineMapper.updateById(orderLine);

balance.setQtyOnHand(scaleQuantity(balance.getQtyOnHand().subtract(requestQty)));
balance.setAmountOnHand(scaleAmount(balance.getAmountOnHand().subtract(returnLine.getAmount())));
inventoryBalanceMapper.updateById(balance);

transaction.setBizType("PURCHASE_RETURN");
transaction.setBizNo(returnEntity.getReturnNo());
transaction.setBizLineId(returnLine.getId());
transaction.setDirection("OUT");
transaction.setQty(requestQty);
transaction.setAmount(scaleAmount(returnLine.getAmount()));
transaction.setUnitCost(scaleUnitCost(returnLine.getAmount(), requestQty));
inventoryTransactionMapper.insert(transaction);
```

```java
private String resolveReceiptStatus(Iterable<PurchaseOrderLineEntity> orderLines) {
    boolean anyReceived = false;
    boolean allReceived = true;
    for (PurchaseOrderLineEntity orderLine : orderLines) {
        BigDecimal receivedQty = safeQuantity(orderLine.getReceivedQty());
        BigDecimal orderQty = scaleQuantity(orderLine.getQty());
        if (receivedQty.compareTo(BigDecimal.ZERO) > 0) {
            anyReceived = true;
        }
        if (receivedQty.compareTo(orderQty) != 0) {
            allReceived = false;
        }
    }
    if (allReceived) {
        return "RECEIVED";
    }
    if (anyReceived) {
        return "PARTIAL_RECEIVED";
    }
    return "NOT_RECEIVED";
}
```

- [x] **Step 4: Re-run the same test and verify it passes**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=PurchaseReturnControllerPostTest" test
```

Expected:

```text
BUILD SUCCESS
```

Rules:
- `post` 仅允许 `DRAFT`
- 关联采购入库单必须为 `POSTED`
- 重复过账必须拦截
- 不允许负库存退货
- `inv_txn` 只追加，不更新历史记录
- 正式过账后本轮不支持反过账

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

- [x] **Step 1: Add failing script and slice-test assertions for purchase return artifacts**

```java
assertThat(Files.exists(Path.of("src/main/resources/db/migration/V15__purchase_return_schema.sql"))).isTrue();
assertThat(createTablesScript).contains("CREATE TABLE IF NOT EXISTS pur_return");
assertThat(createTablesScript).contains("CREATE TABLE IF NOT EXISTS pur_return_line");
assertThat(createTablesScript).contains("returned_qty DECIMAL(18, 4)");
assertThat(createIndexesScript).contains("uk_pur_return_return_no");
assertThat(dictAndConfigScript).contains("PURCHASE_RETURN");
assertThat(dictAndConfigScript).contains("PURCHASE_RETURN', 'PRT', 'yyyyMMdd', 4, 1");
assertThat(securityScript).contains("PRT202605010001");
assertThat(securityScript).contains("PURCHASE_RETURN");
```

```java
@MockBean
private PurchaseReturnMapper purchaseReturnMapper;

@MockBean
private PurchaseReturnLineMapper purchaseReturnLineMapper;
```

- [x] **Step 2: Run script and slice tests to verify they fail before alignment**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=DbScriptLayoutTest,SecurityConfigTest,HealthControllerTest" test
```

Expected:

```text
FAIL ... to contain "CREATE TABLE IF NOT EXISTS pur_return"
```

- [x] **Step 3: Update initialization scripts, duplicate-key handling and demo return data**

```sql
INSERT INTO sys_sequence_rule (id, biz_type, prefix, date_pattern, seq_length, current_value)
VALUES (2005, 'PURCHASE_RETURN', 'PRT', 'yyyyMMdd', 4, 1)
ON DUPLICATE KEY UPDATE
    prefix = VALUES(prefix),
    date_pattern = VALUES(date_pattern),
    seq_length = VALUES(seq_length),
    current_value = VALUES(current_value),
    status = 'ACTIVE';
```

```sql
INSERT INTO pur_return (
    id, company_id, account_book_id, return_no, receipt_id, warehouse_id, return_date, status,
    total_quantity, total_amount, total_tax_amount, remark
) VALUES (
    4805, 1, 1, 'PRT202605010001', 4801, 4501, '2026-05-01', 'POSTED',
    1.0000, 100.00, 13.00, '系统初始化演示采购退货单'
)
ON DUPLICATE KEY UPDATE
    receipt_id = VALUES(receipt_id),
    warehouse_id = VALUES(warehouse_id),
    return_date = VALUES(return_date),
    status = VALUES(status),
    total_quantity = VALUES(total_quantity),
    total_amount = VALUES(total_amount),
    total_tax_amount = VALUES(total_tax_amount),
    deleted_flag = 0,
    remark = VALUES(remark);
```

```sql
INSERT INTO pur_return_line (
    id, return_id, line_no, receipt_line_id, order_line_id, product_id, qty, price, tax_rate, amount, tax_amount, remark
) VALUES (
    4806, 4805, 1, 4802, 4702, 4601, 1.0000, 100.00, 13.0000, 100.00, 13.00, '系统初始化演示采购退货明细'
)
ON DUPLICATE KEY UPDATE
    return_id = VALUES(return_id),
    line_no = VALUES(line_no),
    receipt_line_id = VALUES(receipt_line_id),
    order_line_id = VALUES(order_line_id),
    product_id = VALUES(product_id),
    qty = VALUES(qty),
    price = VALUES(price),
    tax_rate = VALUES(tax_rate),
    amount = VALUES(amount),
    tax_amount = VALUES(tax_amount),
    remark = VALUES(remark);
```

```sql
UPDATE pur_order_line
SET received_qty = 2.0000
WHERE id = 4702;

UPDATE pur_receipt_line
SET returned_qty = 1.0000
WHERE id = 4802;

UPDATE inv_balance
SET qty_on_hand = 2.0000,
    amount_on_hand = 200.00
WHERE id = 4803;
```

```java
if (message.contains("uk_pur_return_return_no")) {
    return "采购退货单号已存在";
}
```

```sql
INSERT INTO inv_txn (
    id, warehouse_id, product_id, biz_type, biz_no, biz_line_id, direction, qty, amount, unit_cost, occurred_time, remark
) VALUES (
    4807, 4501, 4601, 'PURCHASE_RETURN', 'PRT202605010001', 4806, 'OUT', 1.0000, 100.00, 100.0000, '2026-05-01 10:00:00', '系统初始化演示采购退货库存流水'
)
ON DUPLICATE KEY UPDATE
    warehouse_id = VALUES(warehouse_id),
    product_id = VALUES(product_id),
    biz_type = VALUES(biz_type),
    biz_no = VALUES(biz_no),
    biz_line_id = VALUES(biz_line_id),
    direction = VALUES(direction),
    qty = VALUES(qty),
    amount = VALUES(amount),
    unit_cost = VALUES(unit_cost),
    occurred_time = VALUES(occurred_time),
    remark = VALUES(remark);
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
- 初始化脚本必须让采购订单、采购入库、采购退货、库存余额、库存流水五本账对齐
- 演示数据里 `pur_order_line.received_qty`、`pur_receipt_line.returned_qty`、`inv_balance` 要体现“已入 3、已退 1、净存 2”的结果
- `PURCHASE_RETURN` 编号规则和演示退货单号必须对齐，别把第一张正式单直接撞号
- 新增退货 mappers 后，要同步补齐 `@WebMvcTest` 里的 mock

## Self-Review

- 规格覆盖：计划覆盖了采购退货单头表与明细表、`pur_receipt_line.returned_qty` 回写、采购订单 `received_qty / receipt_status` 回写、库存余额冲减、库存流水追加、初始化脚本同步和全量回归，没有漏掉设计文档里的主链路要求。
- 占位检查：全文没有留空步骤、待补充标记或“参照前文自行发挥”这种偷懒写法，每个任务都给了明确文件、测试和命令。
- 类型一致性：退货单状态统一使用 `DRAFT / POSTED / CANCELLED`；库存流水业务类型统一使用 `PURCHASE_RETURN`；库存方向统一使用 `OUT`；采购订单收货状态统一沿用 `NOT_RECEIVED / PARTIAL_RECEIVED / RECEIVED`。

