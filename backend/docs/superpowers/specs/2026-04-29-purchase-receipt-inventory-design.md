# 采购入库与库存落账设计文档

**日期**：2026-04-29  
**项目**：`erpServer`  
**技术栈**：`Spring Boot + MySQL + Redis`  
**主题**：采购入库最小业务闭环设计

---

## 1. 设计目标

本轮目标是在已完成的采购订单模块基础上，补齐 `采购订单 -> 采购入库 -> 库存增加` 的最小业务闭环。设计重点不是把采购、库存、财务一口气炖成一锅，而是先把采购入库事实、库存余额事实和库存流水事实写扎实，保证后续采购退货、销售出库、应付形成都能落在清晰的业务基础上。

本轮设计完成后，系统需要具备以下能力：

- 支持采购入库单草稿新增、详情、分页、草稿编辑、草稿作废、正式过账
- 采购入库必须关联采购订单，且采购订单必须已审批通过
- 支持同一采购订单分批入库
- 正式过账后，系统自动回写采购订单已入库数量与收货状态
- 正式过账后，系统自动增加库存余额并生成库存流水

## 2. 范围与边界

### 2.1 本轮纳入范围

- 采购入库单头表与明细表
- 库存余额表
- 库存流水表
- 采购订单收货状态回写
- 采购订单明细已入库数量回写
- 初始化脚本与演示数据同步
- 自动化测试覆盖迁移、接口、事务回写和脚本一致性

### 2.2 本轮明确不做

- 采购入库单独审批
- 采购退货
- 入库反过账
- 应付单生成与付款核销
- 财务凭证生成
- 库存预占、调拨、批次、序列号、库位
- 复杂成本算法与重算机制

### 2.3 设计约束

- 当前阶段保持模块化单体，不引入异步事件总线
- 当前阶段保持同步事务，过账成功才算业务成功
- 当前阶段采购入库以采购订单明细为唯一来源，不允许客户端自定义成本口径

## 3. 总体方案

采购入库模块采用与采购订单一致的 `controller + service + mapper + model + web` 结构，落在 `purchase/receipt` 包下。库存余额与库存流水先作为同一事务中的持久化对象处理，不另外拆出异步库存域，以降低调试和回归成本。

本轮方案采用“采购订单已审批通过即可入库”的简化模型：

- 采购订单负责审批准入
- 采购入库负责实际业务执行
- 库存余额负责当前库存快照
- 库存流水负责库存变化历史

这样拆的目的很明确：审批和执行先分层，库存事实和业务事实同时落账，但不提前引入工作流引擎、异步一致性或复杂库存中台。

## 4. 数据模型设计

### 4.1 新增表：`pur_receipt`

用途：采购入库单头表，记录一次入库业务的主信息。

建议字段：

- `id`
- `company_id`
- `account_book_id`
- `receipt_no`
- `order_id`
- `warehouse_id`
- `receipt_date`
- `status`
- `total_quantity`
- `total_amount`
- `total_tax_amount`
- `deleted_flag`
- `remark`
- `created_by`
- `created_time`
- `updated_by`
- `updated_time`
- `version`

关键约束：

- `receipt_no` 唯一
- `status` 仅允许 `DRAFT / POSTED / CANCELLED`
- `order_id` 关联 `pur_order`
- `warehouse_id` 关联 `md_warehouse`

### 4.2 新增表：`pur_receipt_line`

用途：采购入库明细，记录本次入库的商品、数量和金额事实。

建议字段：

- `id`
- `receipt_id`
- `line_no`
- `order_line_id`
- `product_id`
- `qty`
- `price`
- `tax_rate`
- `amount`
- `tax_amount`
- `remark`
- `created_by`
- `created_time`
- `updated_by`
- `updated_time`
- `version`

关键约束：

- `receipt_id` 关联 `pur_receipt`
- `order_line_id` 关联 `pur_order_line`
- `line_no` 按单据内从 `1` 递增
- `qty > 0`
- `price`、`tax_rate`、`amount`、`tax_amount` 由服务端按采购订单明细口径生成

### 4.3 新增表：`inv_balance`

用途：库存余额快照，记录每个仓库每个商品的当前结存数量与金额。

建议字段：

- `id`
- `warehouse_id`
- `product_id`
- `qty_on_hand`
- `amount_on_hand`
- `created_by`
- `created_time`
- `updated_by`
- `updated_time`
- `version`

关键约束：

- `warehouse_id + product_id` 唯一
- `qty_on_hand` 表示当前库存数量
- `amount_on_hand` 表示当前库存金额

### 4.4 新增表：`inv_txn`

用途：库存流水表，记录库存变化历史事实。

建议字段：

- `id`
- `warehouse_id`
- `product_id`
- `biz_type`
- `biz_no`
- `biz_line_id`
- `direction`
- `qty`
- `amount`
- `unit_cost`
- `occurred_time`
- `remark`
- `created_by`
- `created_time`

关键约束：

- 当前阶段 `biz_type` 固定支持 `PURCHASE_RECEIPT`
- 当前阶段 `direction` 固定支持 `IN`
- 流水只追加，不更新，不删除

### 4.5 增强现有表：`pur_order`

新增字段：

- `receipt_status`

建议状态：

- `NOT_RECEIVED`
- `PARTIAL_RECEIVED`
- `RECEIVED`

设计目的：

- 让采购订单列表、详情和后续链路能直接展示收货进度
- 减少每次查询时临时聚合入库单的成本

### 4.6 增强现有表：`pur_order_line`

新增字段：

- `received_qty`

设计目的：

- 直接记录订单行累计已入库数量
- 校验剩余可入库数量时使用 `qty - received_qty`
- 让分批入库和超量拦截都能在单事务内完成

## 5. 状态模型设计

### 5.1 采购入库单状态

- `DRAFT`
  - 可编辑
  - 可作废
  - 可正式过账

- `POSTED`
  - 不可编辑
  - 不可重复过账
  - 当前阶段不可反过账

- `CANCELLED`
  - 仅允许由草稿作废得到
  - 不可恢复

### 5.2 采购订单收货状态

- `NOT_RECEIVED`
  - 所有订单行 `received_qty = 0`

- `PARTIAL_RECEIVED`
  - 至少一行已收货，但仍存在剩余可入库数量

- `RECEIVED`
  - 所有订单行都已全部收货

### 5.3 状态关系

- 采购订单 `APPROVED` 才允许创建或过账采购入库
- 同一采购订单允许多张采购入库单
- 同一采购订单行允许多次入库，直到 `received_qty = qty`

## 6. 接口设计

### 6.1 `POST /api/purchase/receipts`

用途：创建采购入库草稿。

请求建议字段：

- 头：`orderId`、`warehouseId`、`receiptDate`、`remark`
- 明细：`orderLineId`、`qty`、`remark`

设计原则：

- 不允许客户端直接传 `price`、`taxRate`、`amount`、`taxAmount`
- 服务端根据采购订单明细带出商品、单价、税率，并重算汇总

### 6.2 `GET /api/purchase/receipts`

用途：采购入库分页查询。

建议查询条件：

- `keyword`
- `orderId`
- `warehouseId`
- `status`
- `receiptDateFrom`
- `receiptDateTo`
- `pageNo`
- `pageSize`

说明：

- `keyword` 当前仅按 `receiptNo` 模糊匹配
- 列表按 `id DESC` 排序

### 6.3 `GET /api/purchase/receipts/{id}`

用途：采购入库详情查询。

返回建议包含：

- 入库单头字段
- 明细字段
- 采购订单号
- 仓库名称
- 商品名称

### 6.4 `PUT /api/purchase/receipts/{id}`

用途：编辑采购入库草稿。

规则：

- 仅允许 `DRAFT`
- 使用整单覆盖明细
- 重新按订单明细口径计算金额和税额

### 6.5 `POST /api/purchase/receipts/{id}/post`

用途：正式过账采购入库。

规则：

- 仅允许 `DRAFT`
- 采购订单必须为 `APPROVED`
- 过账成功后同时更新业务单、订单回写和库存事实

### 6.6 `POST /api/purchase/receipts/{id}/cancel`

用途：作废采购入库草稿。

规则：

- 仅允许 `DRAFT`
- `POSTED` 当前阶段不支持作废

## 7. 过账事务设计

### 7.1 过账校验顺序

建议固定按以下顺序校验：

1. 校验入库单存在且状态为 `DRAFT`
2. 校验采购订单存在且状态为 `APPROVED`
3. 校验仓库存在且状态为 `ACTIVE`
4. 校验每个 `orderLineId` 属于该采购订单
5. 校验每个商品存在且状态为 `ACTIVE`
6. 计算每个订单行剩余可入库数量 `qty - received_qty`
7. 校验本次入库数量大于 `0` 且不超过剩余可入库数量
8. 重算本次明细金额、税额和入库单汇总
9. 在同一事务中完成过账落账

### 7.2 过账落账动作

同一事务内必须完成以下动作：

- 更新 `pur_receipt.status = POSTED`
- 累加 `pur_order_line.received_qty`
- 根据订单行收货完成度回写 `pur_order.receipt_status`
- 对 `inv_balance` 执行新增或累加
- 向 `inv_txn` 追加库存流水

### 7.3 一致性要求

- 任一步失败整单回滚
- 不允许出现“入库单已过账但库存未增加”
- 不允许出现“库存已增加但订单未回写”
- 不允许重复过账同一张入库单

## 8. 金额与库存口径

当前阶段库存余额金额采用采购订单明细的未税金额累计。

设计口径如下：

- `amount = qty * price`
- `tax_amount = amount * tax_rate / 100`
- `inv_balance.amount_on_hand` 按 `amount` 累加
- `inv_txn.unit_cost = amount / qty`

当前阶段不处理：

- 税价合计入库成本
- 移动平均、先进先出、月末一次加权
- 采购费用分摊
- 成本重算

这个口径不是最终版财务成本算法，但足够支撑当前采购入库和库存台账闭环。

## 9. 测试策略

建议按 TDD 分 5 组测试推进：

- `PurchaseReceiptSchemaMigrationTest`
  - 校验 `V14` 新表、增强字段和索引创建成功

- `PurchaseReceiptControllerCreateDetailTest`
  - 校验草稿新增、详情查询、金额汇总和基础关联回显

- `PurchaseReceiptControllerPageAndUpdateTest`
  - 校验分页、筛选、草稿编辑、已过账不可编辑

- `PurchaseReceiptControllerPostTest`
  - 校验正式过账、超量入库拦截、重复过账拦截、订单回写、库存余额增加、库存流水生成

- `DbScriptLayoutTest`
  - 校验初始化脚本同步了采购入库、库存余额、库存流水和演示数据

## 10. 数据初始化与脚本同步要求

初始化脚本需要同步以下内容：

- `pur_receipt`
- `pur_receipt_line`
- `inv_balance`
- `inv_txn`
- `pur_order.receipt_status`
- `pur_order_line.received_qty`

演示数据建议至少包含：

- 一张已审批通过且未完全收货的采购订单
- 一张演示采购入库单
- 一条对应库存余额
- 一条对应库存流水

设计目的：

- 保证脚本可重复执行
- 保证开发和测试环境有可直接验证的业务样例

## 11. 风险与后续演进

本轮最大的技术取舍是：库存余额与库存流水同步事务落账，而不是异步拆域。这样做的好处是实现稳、回归简单、问题定位直接；代价是后续扩展到复杂库存体系时，需要再考虑领域拆分和事件驱动。

后续推荐演进顺序：

1. 采购退货
2. 销售出库
3. 应付形成
4. 库存调整与库存盘点
5. 更完整的库存成本算法

---

## 12. 结论

本设计采用“采购订单审批通过后，采购入库直接执行”的最小闭环方案，优先打通采购执行事实与库存事实。该方案范围受控、实现清晰、测试边界明确，能在当前代码基础上快速形成第一条真实可用的采购到库存主链路，并为后续采购退货、销售出库、应付和库存报表提供稳定基础。
