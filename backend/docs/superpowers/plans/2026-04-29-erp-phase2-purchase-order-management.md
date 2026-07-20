# ERP Purchase Order Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 Spring Boot ERP 骨架上补齐采购订单最小闭环，支持采购订单新增、详情、分页、草稿更新、提交审批、审批通过、审批驳回、作废，并同步数据库初始化脚本。

**Architecture:** 采购订单模块采用 `controller + service + mapper + model + web` 结构，落在 `purchase/order` 包下。当前阶段只实现采购订单头表 `pur_order`、明细表 `pur_order_line` 以及最小单号生成逻辑，审批先以内建状态流转完成，不提前引入通用工作流引擎和采购入库链路，避免把东西写成一锅乱炖。

**Tech Stack:** Spring Boot 3.3.x, MyBatis-Plus, Flyway, H2, MockMvc, JUnit 5

---

## File Map

**Create:**
- `src/main/resources/db/migration/V13__purchase_order_schema.sql`
- `src/main/java/com/tuowei/erp/purchase/order/controller/PurchaseOrderController.java`
- `src/main/java/com/tuowei/erp/purchase/order/service/PurchaseOrderService.java`
- `src/main/java/com/tuowei/erp/purchase/order/service/PurchaseOrderNumberService.java`
- `src/main/java/com/tuowei/erp/purchase/order/mapper/PurchaseOrderMapper.java`
- `src/main/java/com/tuowei/erp/purchase/order/mapper/PurchaseOrderLineMapper.java`
- `src/main/java/com/tuowei/erp/purchase/order/model/PurchaseOrderEntity.java`
- `src/main/java/com/tuowei/erp/purchase/order/model/PurchaseOrderLineEntity.java`
- `src/main/java/com/tuowei/erp/purchase/order/web/PurchaseOrderCreateRequest.java`
- `src/main/java/com/tuowei/erp/purchase/order/web/PurchaseOrderUpdateRequest.java`
- `src/main/java/com/tuowei/erp/purchase/order/web/PurchaseOrderSubmitRequest.java`
- `src/main/java/com/tuowei/erp/purchase/order/web/PurchaseOrderApproveRequest.java`
- `src/main/java/com/tuowei/erp/purchase/order/web/PurchaseOrderRejectRequest.java`
- `src/main/java/com/tuowei/erp/purchase/order/web/PurchaseOrderPageQuery.java`
- `src/main/java/com/tuowei/erp/purchase/order/web/PurchaseOrderLineRequest.java`
- `src/main/java/com/tuowei/erp/purchase/order/web/PurchaseOrderLineResponse.java`
- `src/main/java/com/tuowei/erp/purchase/order/web/PurchaseOrderResponse.java`
- `src/test/java/com/tuowei/erp/purchase/order/PurchaseOrderSchemaMigrationTest.java`
- `src/test/java/com/tuowei/erp/purchase/order/PurchaseOrderControllerCreateDetailTest.java`
- `src/test/java/com/tuowei/erp/purchase/order/PurchaseOrderControllerPageAndUpdateTest.java`
- `src/test/java/com/tuowei/erp/purchase/order/PurchaseOrderControllerWorkflowTest.java`

**Modify:**
- `db/init/02_create_tables.sql`
- `db/init/03_create_indexes.sql`
- `db/init/05_init_security.sql`
- `src/main/java/com/tuowei/erp/common/exception/GlobalExceptionHandler.java`
- `src/test/java/com/tuowei/erp/db/DbScriptLayoutTest.java`
- `src/test/java/com/tuowei/erp/common/security/SecurityConfigTest.java`
- `src/test/java/com/tuowei/erp/system/controller/HealthControllerTest.java`

## Task 1: Add Purchase Order Schema

**Files:**
- Create: `src/main/resources/db/migration/V13__purchase_order_schema.sql`
- Create: `src/test/java/com/tuowei/erp/purchase/order/PurchaseOrderSchemaMigrationTest.java`

- [x] **Step 1: Write the failing migration test**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=PurchaseOrderSchemaMigrationTest test` and verify it fails because `pur_order` / `pur_order_line` do not exist**
- [x] **Step 3: Add `pur_order` and `pur_order_line` tables plus unique/index definitions**
- [x] **Step 4: Re-run the same test and verify it passes**

Rules:
- `pur_order.order_no` 唯一索引固定命名为 `uk_pur_order_order_no`
- `pur_order` 必须包含 `company_id`、`account_book_id`、`order_no`、`supplier_id`、`order_date`、`delivery_date`、`status`、`approval_status`、`total_quantity`、`total_amount`、`total_tax_amount`、`deleted_flag`、`remark`、审计字段
- `pur_order_line` 必须包含 `order_id`、`line_no`、`product_id`、`qty`、`price`、`tax_rate`、`tax_amount`、`amount`、`remark`
- 建立 `idx_pur_order_supplier_id`、`idx_pur_order_order_date`、`idx_pur_order_status`、`idx_pur_order_line_order_id`

## Task 2: Implement Purchase Order Create and Detail APIs

**Files:**
- Create: `src/main/java/com/tuowei/erp/purchase/order/model/PurchaseOrderEntity.java`
- Create: `src/main/java/com/tuowei/erp/purchase/order/model/PurchaseOrderLineEntity.java`
- Create: `src/main/java/com/tuowei/erp/purchase/order/mapper/PurchaseOrderMapper.java`
- Create: `src/main/java/com/tuowei/erp/purchase/order/mapper/PurchaseOrderLineMapper.java`
- Create: `src/main/java/com/tuowei/erp/purchase/order/web/PurchaseOrderCreateRequest.java`
- Create: `src/main/java/com/tuowei/erp/purchase/order/web/PurchaseOrderLineRequest.java`
- Create: `src/main/java/com/tuowei/erp/purchase/order/web/PurchaseOrderLineResponse.java`
- Create: `src/main/java/com/tuowei/erp/purchase/order/web/PurchaseOrderResponse.java`
- Create: `src/main/java/com/tuowei/erp/purchase/order/service/PurchaseOrderNumberService.java`
- Create: `src/main/java/com/tuowei/erp/purchase/order/service/PurchaseOrderService.java`
- Create: `src/main/java/com/tuowei/erp/purchase/order/controller/PurchaseOrderController.java`
- Create: `src/test/java/com/tuowei/erp/purchase/order/PurchaseOrderControllerCreateDetailTest.java`

- [x] **Step 1: Write the failing integration test for order creation, detail query, totals calculation and duplicate order number rejection**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=PurchaseOrderControllerCreateDetailTest test` and verify it fails because `/api/purchase/orders` does not exist**
- [x] **Step 3: Implement minimal create/detail flow, line persistence and local sequence-based order number generation**
- [x] **Step 4: Re-run the same test and verify it passes**

Rules:
- 创建时必须校验供应商存在且未删除、状态为 `ACTIVE`
- 明细不能为空，且每行 `productId`、`qty`、`price`、`taxRate` 必填
- `qty > 0`、`price >= 0`、`taxRate >= 0`
- 总数量、未税金额、税额统一由服务端基于明细重算，别相信前端传参
- 新建单据默认 `status = DRAFT`、`approval_status = NOT_SUBMITTED`
- 单号格式使用 `sys_sequence_rule` 中 `PURCHASE_ORDER` 规则，预期形如 `PO202604290001`

## Task 3: Implement Purchase Order Page Query and Draft Update

**Files:**
- Create: `src/main/java/com/tuowei/erp/purchase/order/web/PurchaseOrderPageQuery.java`
- Create: `src/main/java/com/tuowei/erp/purchase/order/web/PurchaseOrderUpdateRequest.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/order/service/PurchaseOrderService.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/order/controller/PurchaseOrderController.java`
- Create: `src/test/java/com/tuowei/erp/purchase/order/PurchaseOrderControllerPageAndUpdateTest.java`

- [x] **Step 1: Write the failing test for page query, keyword/status filter and draft-only update**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=PurchaseOrderControllerPageAndUpdateTest test` and verify it fails because list/update logic is incomplete**
- [x] **Step 3: Implement minimal `GET /api/purchase/orders` and `PUT /api/purchase/orders/{id}`**
- [x] **Step 4: Re-run the same test and verify it passes**

Rules:
- 只允许 `DRAFT` 或 `REJECTED` 单据被编辑
- 更新时整单覆盖明细，旧明细先删后插入
- 支持按 `keyword` 模糊匹配 `order_no`
- 支持按 `status`、`approvalStatus`、`supplierId` 过滤
- 默认 `pageNo = 1`、`pageSize = 20`，`pageSize` 最大 `200`
- 列表按 `id DESC` 排序

## Task 4: Implement Submit, Approve, Reject and Cancel APIs

**Files:**
- Create: `src/main/java/com/tuowei/erp/purchase/order/web/PurchaseOrderSubmitRequest.java`
- Create: `src/main/java/com/tuowei/erp/purchase/order/web/PurchaseOrderApproveRequest.java`
- Create: `src/main/java/com/tuowei/erp/purchase/order/web/PurchaseOrderRejectRequest.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/order/service/PurchaseOrderService.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/order/controller/PurchaseOrderController.java`
- Create: `src/test/java/com/tuowei/erp/purchase/order/PurchaseOrderControllerWorkflowTest.java`

- [x] **Step 1: Write the failing workflow test for submit, approve, reject, resubmit and cancel**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=PurchaseOrderControllerWorkflowTest test` and verify it fails because lifecycle APIs do not exist**
- [x] **Step 3: Implement minimal state transition endpoints**
- [x] **Step 4: Re-run the same test and verify it passes**

Rules:
- `POST /api/purchase/orders/{id}/submit`：仅允许 `DRAFT`、`REJECTED` 提交，流转到 `status = SUBMITTED`、`approval_status = IN_APPROVAL`
- `POST /api/purchase/orders/{id}/approve`：仅允许已提交单据审批通过，流转到 `status = APPROVED`、`approval_status = APPROVED`
- `POST /api/purchase/orders/{id}/reject`：仅允许已提交单据驳回，流转到 `status = REJECTED`、`approval_status = REJECTED`
- `POST /api/purchase/orders/{id}/cancel`：仅允许未执行入库的 `DRAFT`、`REJECTED`、`SUBMITTED` 单据作废，流转到 `status = CANCELLED`、`approval_status = CANCELLED`
- 当前阶段先不接审批人权限隔离和审批历史表，但错误提示要明确

## Task 5: Align Initialization Scripts and Run Regression

**Files:**
- Modify: `db/init/02_create_tables.sql`
- Modify: `db/init/03_create_indexes.sql`
- Modify: `db/init/05_init_security.sql`
- Modify: `src/main/java/com/tuowei/erp/common/exception/GlobalExceptionHandler.java`
- Modify: `src/test/java/com/tuowei/erp/db/DbScriptLayoutTest.java`
- Modify: `src/test/java/com/tuowei/erp/common/security/SecurityConfigTest.java`
- Modify: `src/test/java/com/tuowei/erp/system/controller/HealthControllerTest.java`

- [x] **Step 1: Add failing script assertions for purchase order tables, indexes and demo order seed**
- [x] **Step 2: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=DbScriptLayoutTest test` and verify it fails before script alignment**
- [x] **Step 3: Update initialization scripts, duplicate-key message mapping and WebMvc slice mocks**
- [x] **Step 4: Re-run `DbScriptLayoutTest` and verify it passes**
- [x] **Step 5: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" test` and fix any regression**

Rules:
- 初始化脚本保持可重复执行
- 默认种子至少包含一个演示采购订单，如 `PO202604280001`
- `GlobalExceptionHandler` 至少补充 `uk_pur_order_order_no -> 采购订单号已存在`
- 新增 mapper 后同步补齐 `@WebMvcTest` 场景 mock，别让切片测试又炸得跟二踢脚似的

