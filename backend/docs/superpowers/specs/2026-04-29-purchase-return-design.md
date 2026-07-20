# 采购退货设计文档

**日期**：2026-04-29  
**项目**：`erpServer`  
**技术栈**：`Spring Boot + MySQL + Redis`  
**主题**：采购退货最小业务闭环设计

---

## 1. 设计目标

本轮目标是在已完成的“采购订单 -> 采购入库 -> 库存增加”主链路基础上，继续补齐“采购退货 -> 库存减少 -> 采购收货进度回写”的最小业务闭环。

这轮不是去堆审批、退款、财务红字单那一大坨花活，而是先把退货事实、库存冲减事实和采购收货进度回写做扎实。只有这层基础打稳，后面供应商退款、应付调整、财务联动才不会变成烂账现场。

本轮设计完成后，系统需要具备以下能力：

- 支持采购退货单草稿新增、详情、分页、草稿编辑、草稿作废、正式过账
- 采购退货单必须基于一张已过账采购入库单创建
- 支持按采购入库明细分批退货
- 正式过账后，系统自动冲减库存余额并生成库存流水
- 正式过账后，系统自动回写采购订单明细已收货数量与采购订单收货状态

## 2. 范围与边界

### 2.1 本轮纳入范围

- 采购退货单头表与明细表
- 采购入库明细累计退货数量回写
- 采购订单明细已收货数量回写
- 采购订单收货状态回写
- 库存余额冲减
- 库存流水追加
- 初始化脚本与演示数据同步
- 自动化测试覆盖迁移、接口、过账事务、脚本一致性

### 2.2 本轮明确不做

- 采购退货独立审批
- 采购退货反过账
- 供应商退款、应付红字单、付款核销
- 财务凭证生成
- 跨采购入库单混退
- 负库存退货
- 批次、序列号、库位
- 复杂成本重算
- 与发票、税务、对账单联动

### 2.3 设计约束

- 当前阶段保持模块化单体，不引入异步事件总线
- 当前阶段保持同步事务，过账成功才算业务成功
- 当前阶段采购退货必须基于采购入库明细，不允许直接对采购订单退货
- 当前阶段退货价格、税率必须继承原采购入库明细，不允许客户端手工改成本口径

## 3. 总体方案

采购退货模块继续沿用现有采购链路风格，采用 `controller + service + mapper + model + web` 结构，落在 `purchase/returnorder` 或 `purchase/return` 包下，最终实现时以代码库现有命名风格为准。

本轮方案采用“基于已过账采购入库单退货”的简化模型：

- 采购入库负责形成原始收货事实
- 采购退货负责形成退货事实
- 库存余额负责当前库存快照
- 库存流水负责库存变化历史
- 采购订单负责承接最终收货进度

这么拆的核心目的很明确：退货必须能追溯到原入库事实，库存冲减和采购收货进度回写必须同事务落账，先把业务账、库存账、进度账对齐，别为了看起来“高级”先引一堆审批流和财务流把自己绕死。

## 4. 数据模型设计

### 4.1 新增表：`pur_return`

用途：采购退货单头表，记录一次退货业务的主信息。

建议字段：

- `id`
- `company_id`
- `account_book_id`
- `return_no`
- `receipt_id`
- `warehouse_id`
- `return_date`
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

- `return_no` 唯一
- `receipt_id` 关联 `pur_receipt`
- 一张退货单只能关联一张采购入库单
- `warehouse_id` 跟随来源采购入库单锁定
- `status` 仅允许 `DRAFT / POSTED / CANCELLED`

### 4.2 新增表：`pur_return_line`

用途：采购退货明细，记录本次退货的商品、数量和金额事实。

建议字段：

- `id`
- `return_id`
- `line_no`
- `receipt_line_id`
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

- `return_id` 关联 `pur_return`
- `receipt_line_id` 关联 `pur_receipt_line`
- `order_line_id` 关联 `pur_order_line`
- `line_no` 按单据内从 `1` 递增
- `qty > 0`
- `price`、`tax_rate`、`amount`、`tax_amount` 由服务端按原采购入库明细口径生成

### 4.3 增强现有表：`pur_receipt_line`

新增字段：

- `returned_qty`

设计目的：

- 直接记录采购入库明细累计已退货数量
- 校验可退数量时使用 `qty - returned_qty`
- 避免每次退货过账都去聚合历史退货单，把代码和查询都写成面条

### 4.4 采购订单回写表

本轮不新增采购订单退货专用状态表，继续沿用：

- `pur_order.receipt_status`
- `pur_order_line.received_qty`

设计目的：

- 采购退货过账后直接冲减 `received_qty`
- 再统一按订单行状态重新计算 `receipt_status`
- 保证采购订单展示的是“净收货进度”，而不是假装没退过货

### 4.5 库存表复用

本轮不新增新的库存表，继续沿用：

- `inv_balance`
- `inv_txn`

设计原则：

- `inv_balance` 负责当前库存快照
- `inv_txn` 负责库存变化历史
- 采购退货过账时：
  - 冲减 `inv_balance`
  - 追加 `inv_txn`
  - `biz_type = PURCHASE_RETURN`
  - `direction = OUT`

## 5. 状态模型设计

### 5.1 采购退货单状态

- `DRAFT`
  - 可编辑
  - 可作废
  - 可正式过账

- `POSTED`
  - 已生效
  - 不可编辑
  - 不可作废
  - 本轮不可反过账

- `CANCELLED`
  - 仅允许由草稿作废得到
  - 不可恢复

### 5.2 采购入库明细退货进度

本轮不单独落一个 `return_status` 字段，直接由 `pur_receipt_line.returned_qty` 推导：

- `returned_qty = 0`：未退
- `0 < returned_qty < qty`：部分退
- `returned_qty = qty`：已退完

这么设计是为了少维护一层容易脏的数据状态，避免后面出现“数量对不上但状态写得挺好看”的低级事故。

### 5.3 采购订单收货状态

继续沿用：

- `NOT_RECEIVED`
- `PARTIAL_RECEIVED`
- `RECEIVED`

采购退货正式过账后：

- 扣减 `pur_order_line.received_qty`
- 重新计算 `pur_order.receipt_status`

也就是说，采购退货不是单纯冲库存，它还会把采购主链路里的“净收货事实”回写回来。

## 6. 接口设计

### 6.1 `POST /api/purchase/returns`

用途：创建采购退货草稿。

请求建议字段：

- 头：`receiptId`、`returnDate`、`remark`
- 明细：`receiptLineId`、`qty`、`remark`

设计原则：

- 不允许客户端直接传 `productId`、`orderLineId`、`price`、`taxRate`、`amount`、`taxAmount`
- 服务端根据采购入库明细带出商品、原订单行、单价、税率，并重算汇总
- 只能关联 `POSTED` 的采购入库单

### 6.2 `GET /api/purchase/returns`

用途：采购退货分页查询。

建议查询条件：

- `keyword`
- `receiptId`
- `warehouseId`
- `status`
- `returnDateFrom`
- `returnDateTo`
- `pageNo`
- `pageSize`

说明：

- `keyword` 当前仅按 `returnNo` 模糊匹配
- 列表按 `id DESC`

### 6.3 `GET /api/purchase/returns/{id}`

用途：采购退货详情查询。

返回建议包含：

- 退货单头字段
- 明细字段
- `receiptNo`
- `orderNo`
- `warehouseName`
- `productName`
- 原入库数量
- 累计已退数量
- 本次可退数量

这些信息不回显，前端就得自己拼接推导，纯属没事找事。

### 6.4 `PUT /api/purchase/returns/{id}`

用途：编辑采购退货草稿。

规则：

- 仅允许 `DRAFT`
- 使用整单覆盖明细
- `receiptId` 创建后不允许改
- 服务端重新按原采购入库明细口径计算金额和税额

### 6.5 `POST /api/purchase/returns/{id}/cancel`

用途：作废采购退货草稿。

规则：

- 仅允许 `DRAFT`

### 6.6 `POST /api/purchase/returns/{id}/post`

用途：正式过账采购退货。

规则：

- 仅允许 `DRAFT`
- 关联采购入库单必须为 `POSTED`
- 过账成功后同时更新退货单、入库明细、采购订单和库存事实

## 7. 过账事务设计

### 7.1 过账校验顺序

建议固定按以下顺序校验：

1. 校验退货单存在且状态为 `DRAFT`
2. 校验关联采购入库单存在且状态为 `POSTED`
3. 校验仓库存在且状态为 `ACTIVE`
4. 校验每个 `receiptLineId` 都属于该采购入库单
5. 校验每个商品存在且状态为 `ACTIVE`
6. 计算每条入库明细可退数量 `receipt_line.qty - receipt_line.returned_qty`
7. 校验本次退货数量大于 `0` 且不超过可退数量
8. 校验当前库存余额 `qty_on_hand >= 本次退货数量`
9. 重算本次退货金额、税额和退货单汇总
10. 在同一事务中完成过账落账

### 7.2 过账落账动作

同一事务内必须完成以下动作：

- 更新 `pur_return.status = POSTED`
- 累加 `pur_receipt_line.returned_qty`
- 扣减 `pur_order_line.received_qty`
- 根据订单行净收货完成度回写 `pur_order.receipt_status`
- 对 `inv_balance` 执行扣减
- 向 `inv_txn` 追加库存流水

库存流水关键口径：

- `biz_type = PURCHASE_RETURN`
- `direction = OUT`
- `biz_no = return_no`
- `biz_line_id = return_line.id`
- `unit_cost = amount / qty`

### 7.3 一致性要求

- 任一步失败整单回滚
- 不允许出现“退货单已过账但库存未减少”
- 不允许出现“库存已减少但采购订单收货进度没回写”
- 不允许重复过账同一张退货单
- 不允许库存被冲成负数
- 库存流水只追加，不更新历史记录

## 8. 金额与库存口径

当前阶段退货金额口径继续沿用采购入库的未税金额逻辑。

设计口径如下：

- `amount = qty * price`
- `tax_amount = amount * tax_rate / 100`
- `inv_balance.amount_on_hand` 按 `amount` 冲减
- `inv_txn.unit_cost = amount / qty`

当前阶段不处理：

- 退货运费、折让、价差分摊
- 移动平均、先进先出、月末一次加权
- 成本重算
- 供应商退款与财务红字联动

这个口径不是最终版财务成本算法，但足够支撑当前采购退货与库存台账闭环。

## 9. 测试策略

建议按 TDD 分 5 组测试推进：

- `PurchaseReturnSchemaMigrationTest`
  - 校验 `pur_return`、`pur_return_line`、`pur_receipt_line.returned_qty` 和关键索引创建成功

- `PurchaseReturnControllerCreateDetailTest`
  - 校验退货草稿新增、详情查询、金额汇总和基础关联回显
  - 校验只能关联已过账采购入库单

- `PurchaseReturnControllerPageAndUpdateTest`
  - 校验分页、筛选、草稿编辑、草稿作废、已过账不可编辑

- `PurchaseReturnControllerPostTest`
  - 校验正式过账、超量退货拦截、库存不足拦截、重复过账拦截
  - 校验采购订单回写、库存余额冲减、库存流水生成

- `DbScriptLayoutTest`
  - 校验初始化脚本同步了采购退货、库存流水和演示数据

## 10. 数据初始化与脚本同步要求

初始化脚本需要同步以下内容：

- `pur_return`
- `pur_return_line`
- `pur_receipt_line.returned_qty`
- `PURCHASE_RETURN` 编号规则
- 演示采购退货单
- 演示库存流水

编号规则建议：

- `biz_type = PURCHASE_RETURN`
- `prefix = PRT`
- 格式建议：`PRTyyyyMMdd####`

演示数据建议至少包含：

- 一张已过账采购入库单
- 一张基于该入库单的演示采购退货单
- 一条对应退货明细
- 一条退货后的演示库存余额
- 一条 `PURCHASE_RETURN` 类型库存流水

设计目的：

- 保证脚本可重复执行
- 保证开发和测试环境有可直接验证的退货业务样例
- 保证演示采购订单、采购入库、采购退货、库存余额、库存流水五本账能对上

## 11. 风险与后续演进

本轮最大的技术取舍是：采购退货严格绑定采购入库单，并在同事务内完成库存冲减和采购收货进度回写，而不是先做一个“只记录退货单据，其他以后再算”的半拉子模型。

这样做的好处是：

- 业务追溯清晰
- 库存事实稳定
- 采购进度可信
- 后续供应商退款、应付红字、报表分析都有干净基础

代价是：

- 过账校验和回写点更多
- 事务逻辑更重一些

但这代价是该付的，不然你今天图省事，明天就得花十倍时间对烂账。

后续推荐演进顺序：

1. 供应商退款 / 应付红字调整
2. 销售出库
3. 库存调整与库存盘点
4. 更完整的库存成本算法
5. 更完整的采购结算与财务联动

## 12. 结论

本设计采用“基于已过账采购入库单发起采购退货”的最小闭环方案，优先打通退货业务事实、库存事实和采购收货进度事实。该方案范围受控、实现清晰、测试边界明确，能在当前代码基础上快速形成第二条真实可用的采购库存主链路，并为后续供应商退款、应付调整、库存报表和财务联动提供稳定基础。
