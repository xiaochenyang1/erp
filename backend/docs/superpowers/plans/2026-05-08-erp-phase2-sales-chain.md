# ERP Sales Chain Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐销售订单、销售出库、销售退货最小闭环，支持分批出库、不允许负库存、退货回补库存，并接入后端数据权限。

**Architecture:** 销售域沿用采购域已稳定的 `controller + service + mapper + model + web` 结构，落在 `src/main/java/com/tuowei/erp/sales` 下。销售订单负责审批与业务源头，销售出库负责扣减库存并累计订单出库进度，销售退货负责回补库存并回写净出库进度；财务应收/收款放到下一 Epic，不在本计划里硬塞，省得模块边界被写成东北乱炖。

**Tech Stack:** Spring Boot 3.3.x, Spring Security, MyBatis-Plus, Flyway, H2, MockMvc, JUnit 5

---

## File Map

**Create:**
- `src/main/resources/db/migration/V17__sales_order_delivery_schema.sql`
- `src/main/java/com/tuowei/erp/sales/support/SalesAmountCalculator.java`
- `src/main/java/com/tuowei/erp/sales/support/SalesDeliveryQuantities.java`
- `src/main/java/com/tuowei/erp/sales/support/SalesReturnQuantities.java`
- `src/main/java/com/tuowei/erp/sales/order/**`
- `src/main/java/com/tuowei/erp/sales/delivery/**`
- `src/main/java/com/tuowei/erp/sales/returnorder/**`
- `src/test/java/com/tuowei/erp/sales/order/**`
- `src/test/java/com/tuowei/erp/sales/delivery/**`
- `src/test/java/com/tuowei/erp/sales/returnorder/**`

**Modify:**
- `src/main/java/com/tuowei/erp/common/security/PermissionCodes.java`
- `src/main/java/com/tuowei/erp/common/security/DataScopeService.java`
- `src/main/java/com/tuowei/erp/inventory/stock/service/InventoryPostingService.java`
- `src/main/java/com/tuowei/erp/common/exception/GlobalExceptionHandler.java`
- MVC slice tests that need new mapper mocks

## Task 1: Add Sales Schema

**Files:**
- Create: `src/main/resources/db/migration/V17__sales_order_delivery_schema.sql`
- Test: `src/test/java/com/tuowei/erp/sales/order/SalesSchemaMigrationTest.java`

- [x] Write failing migration test asserting `sal_order`, `sal_order_line`, `sal_delivery`, `sal_delivery_line`, `sal_return`, `sal_return_line` exist.
- [x] Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=SalesSchemaMigrationTest" test`; expected failure is missing sales tables.
- [x] Add `V17` migration with sales order/delivery/return tables, unique indexes for `order_no`、`delivery_no`、`return_no`, and source-line indexes.
- [x] Re-run the same test; expected `BUILD SUCCESS`.

Rules:
- `sal_order_line.delivered_qty` tracks net delivered quantity.
- `sal_delivery_line.returned_qty` tracks returned quantity against posted delivery lines.
- Header tables keep `company_id`、`account_book_id`、`status`、`deleted_flag`、audit fields and `version`.

## Task 2: Implement Sales Order APIs

**Files:**
- Create: `src/main/java/com/tuowei/erp/sales/order/controller/SalesOrderController.java`
- Create: `src/main/java/com/tuowei/erp/sales/order/service/SalesOrderService.java`
- Create: `src/main/java/com/tuowei/erp/sales/order/service/SalesOrderNumberService.java`
- Create: `src/main/java/com/tuowei/erp/sales/order/service/SalesOrderLookupService.java`
- Create: `src/main/java/com/tuowei/erp/sales/order/service/SalesOrderDeliveryStatusService.java`
- Create: `src/main/java/com/tuowei/erp/sales/order/mapper/SalesOrderMapper.java`
- Create: `src/main/java/com/tuowei/erp/sales/order/mapper/SalesOrderLineMapper.java`
- Create: `src/main/java/com/tuowei/erp/sales/order/model/SalesOrderEntity.java`
- Create: `src/main/java/com/tuowei/erp/sales/order/model/SalesOrderLineEntity.java`
- Create: `src/main/java/com/tuowei/erp/sales/order/web/*.java`
- Test: `src/test/java/com/tuowei/erp/sales/order/SalesOrderControllerCreateDetailTest.java`
- Test: `src/test/java/com/tuowei/erp/sales/order/SalesOrderControllerPageAndUpdateTest.java`
- Test: `src/test/java/com/tuowei/erp/sales/order/SalesOrderControllerWorkflowTest.java`
- Test: `src/test/java/com/tuowei/erp/sales/order/SalesOrderDataScopeTest.java`

- [x] Write failing create/detail test for totals, customer validation, generated `SOyyyyMMdd####`, line persistence.
- [x] Run targeted test; expected failure is missing `/api/sales/orders`.
- [x] Implement create/detail with `DRAFT / NOT_SUBMITTED`, server-side totals, active customer/product validation.
- [x] Add list/update test for filters and draft-only update.
- [x] Implement list/update with data scope and full line replacement.
- [x] Add workflow test for submit/approve/reject/cancel.
- [x] Implement workflow endpoints.
- [x] Add data-scope test for department/self visibility and detail blocking.

Rules:
- Sales order creation accepts line `price` and `taxRate`; totals are always recalculated on server.
- Only `DRAFT` and `REJECTED` can be edited or submitted.
- Only submitted orders can be approved/rejected.
- Approved orders are the only source allowed for delivery.

## Task 3: Implement Sales Delivery APIs And Inventory Outbound

**Files:**
- Create: `src/main/java/com/tuowei/erp/sales/delivery/controller/SalesDeliveryController.java`
- Create: `src/main/java/com/tuowei/erp/sales/delivery/service/SalesDeliveryService.java`
- Create: `src/main/java/com/tuowei/erp/sales/delivery/service/SalesDeliveryNumberService.java`
- Create: `src/main/java/com/tuowei/erp/sales/delivery/mapper/SalesDeliveryMapper.java`
- Create: `src/main/java/com/tuowei/erp/sales/delivery/mapper/SalesDeliveryLineMapper.java`
- Create: `src/main/java/com/tuowei/erp/sales/delivery/model/SalesDeliveryEntity.java`
- Create: `src/main/java/com/tuowei/erp/sales/delivery/model/SalesDeliveryLineEntity.java`
- Create: `src/main/java/com/tuowei/erp/sales/delivery/web/*.java`
- Modify: `src/main/java/com/tuowei/erp/inventory/stock/service/InventoryPostingService.java`
- Test: `src/test/java/com/tuowei/erp/sales/delivery/SalesDeliveryControllerCreateDetailTest.java`
- Test: `src/test/java/com/tuowei/erp/sales/delivery/SalesDeliveryControllerPageAndUpdateTest.java`
- Test: `src/test/java/com/tuowei/erp/sales/delivery/SalesDeliveryControllerPostTest.java`
- Test: `src/test/java/com/tuowei/erp/sales/delivery/SalesDeliveryDataScopeTest.java`

- [x] Write failing draft create/detail test based on approved sales order.
- [x] Implement draft create/detail inheriting product/price/tax from sales order lines.
- [x] Write failing page/update/cancel test.
- [x] Implement list/update/cancel with `DRAFT` restriction and warehouse data-scope filtering.
- [x] Write failing post test for inventory deduction, `delivered_qty` writeback, `delivery_status` refresh, over-delivery rejection and insufficient stock rejection.
- [x] Add `InventoryPostingService.postOutbound(command, audit, shortageMessage)` overload so sales can return `库存不足，不能执行销售出库` while purchase keeps old message.
- [x] Implement post transaction with `bizType = SALES_DELIVERY` and `direction = OUT`.

Rules:
- Delivery can only reference an `APPROVED` sales order.
- Delivery qty cannot exceed remaining deliverable qty.
- Posting uses inventory service; sales service cannot directly update `inv_balance`.
- First phase forbids negative stock.

## Task 4: Implement Sales Return APIs And Inventory Inbound

**Files:**
- Create: `src/main/java/com/tuowei/erp/sales/returnorder/controller/SalesReturnController.java`
- Create: `src/main/java/com/tuowei/erp/sales/returnorder/service/SalesReturnService.java`
- Create: `src/main/java/com/tuowei/erp/sales/returnorder/service/SalesReturnNumberService.java`
- Create: `src/main/java/com/tuowei/erp/sales/returnorder/mapper/SalesReturnMapper.java`
- Create: `src/main/java/com/tuowei/erp/sales/returnorder/mapper/SalesReturnLineMapper.java`
- Create: `src/main/java/com/tuowei/erp/sales/returnorder/model/SalesReturnEntity.java`
- Create: `src/main/java/com/tuowei/erp/sales/returnorder/model/SalesReturnLineEntity.java`
- Create: `src/main/java/com/tuowei/erp/sales/returnorder/web/*.java`
- Test: `src/test/java/com/tuowei/erp/sales/returnorder/SalesReturnControllerCreateDetailTest.java`
- Test: `src/test/java/com/tuowei/erp/sales/returnorder/SalesReturnControllerPageAndUpdateTest.java`
- Test: `src/test/java/com/tuowei/erp/sales/returnorder/SalesReturnControllerPostTest.java`
- Test: `src/test/java/com/tuowei/erp/sales/returnorder/SalesReturnDataScopeTest.java`

- [x] Write failing draft create/detail test based on posted sales delivery.
- [x] Implement draft create/detail inheriting delivery line pricing.
- [x] Write failing page/update/cancel test.
- [x] Implement list/update/cancel with `DRAFT` restriction and warehouse data-scope filtering.
- [x] Write failing post test for inventory inbound, `returned_qty` writeback, order net delivered qty rollback and over-return rejection.
- [x] Implement post transaction with `bizType = SALES_RETURN` and `direction = IN`.

Rules:
- Return can only reference a `POSTED` sales delivery.
- Return qty cannot exceed delivery line remaining returnable qty.
- Posting increments inventory and decrements order line net delivered qty.

## Task 5: Wire Permissions, Duplicate Messages And Regression

**Files:**
- Modify: `src/main/java/com/tuowei/erp/common/security/PermissionCodes.java`
- Modify: `src/main/java/com/tuowei/erp/common/security/DataScopeService.java`
- Modify: `src/main/java/com/tuowei/erp/common/exception/GlobalExceptionHandler.java`
- Test: relevant security/slice tests

- [x] Add permission constants for `sales:order:*`, `sales:delivery:*`, `sales:return:*`.
- [x] Add data-scope methods for sales order, delivery and return.
- [x] Add duplicate-key messages for sales order/delivery/return numbers.
- [x] Run sales targeted tests.
- [x] Run purchase/inventory regression.
- [x] Run full `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" test`.

## Self-Review

- 规格覆盖：覆盖销售订单、销售出库、销售退货、库存扣减与回补、数据权限、权限码和回归验证；财务应收/收款明确留给轻量财务 Epic。
- 占位检查：没有 `TODO`、`TBD` 或“自行补齐”类占位。
- 类型一致性：销售订单状态沿用 `DRAFT / SUBMITTED / APPROVED / REJECTED / CANCELLED`，销售出库和销售退货状态沿用 `DRAFT / POSTED / CANCELLED`，库存业务类型固定为 `SALES_DELIVERY / SALES_RETURN`。

