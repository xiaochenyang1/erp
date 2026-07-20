# 库存预占运营闭环设计

## 背景

上一阶段已经实现生产库存预占：销售订单审批时写入 `inv_reservation` 并增加 `inv_balance.qty_reserved`，销售出库和销售订单作废时释放预占。当前能力能保证库存可用量不被抢占，但生产运维层面还缺少可观察、可追溯、可巡检和受控修复能力。

本阶段只补齐库存预占运营闭环，不引入批次、库位、拣货、缺货策略、反审变更等仓储或销售新模型。

## 目标

- 提供预占看板，按仓库、商品、来源类型和状态汇总预占、释放、剩余和可用数量。
- 提供预占列表、详情和来源追溯，能从预占记录定位销售订单、销售订单明细和出库释放情况。
- 提供一致性巡检，只发现异常，不自动改账。
- 提供人工异常释放能力，必须权限控制、乐观锁校验、原因必填和审计留痕。
- 保持现有库存过账、销售审批、销售出库主流程兼容。

## 非目标

- 不实现库位库存、批次/效期库存、拣货任务。
- 不实现销售订单反审、缺货审批、缺货分配策略。
- 不实现定时自动释放或自动修复库存余额。
- 不删除正式测试，不提交与本阶段无关的未跟踪文档。

## 方案选择

采用“受控运营闭环”方案。

系统提供查询、巡检和人工释放能力。巡检接口只返回问题清单，不自动写库；释放接口由具备权限的运营人员触发，并记录事件和审计日志。这样能保证生产环境里每一次异常处理都有明确操作人、原因和数量变化。

没有采用自动自愈方案，因为当前销售订单、出库单、预占记录之间的业务边界还需要运营可见化沉淀。自动释放一旦误判，会直接影响可售库存和订单履约。

## 数据设计

保留现有 `inv_reservation` 作为预占当前状态表。

新增 `inv_reservation_event` 作为预占事件表：

- `id`
- `company_id`
- `account_book_id`
- `reservation_id`
- `warehouse_id`
- `product_id`
- `source_type`
- `source_id`
- `source_no`
- `source_line_id`
- `event_type`
- `event_qty`
- `remaining_qty_before`
- `remaining_qty_after`
- `reason`
- `created_by`
- `created_time`

事件类型：

- `RESERVE`：销售订单审批创建预占。
- `RELEASE`：销售出库消耗预占。
- `MANUAL_RELEASE`：人工异常释放预占。

索引：

- `idx_inv_reservation_event_company_reservation`：`company_id, reservation_id, created_time`
- `idx_inv_reservation_event_company_source`：`company_id, source_type, source_id`
- `idx_inv_reservation_event_company_balance`：`company_id, warehouse_id, product_id, created_time`

## 服务边界

新增 `InventoryReservationOpsService` 负责运营查询、巡检和人工释放入口。它依赖现有 `InventoryReservationMapper`、`InventoryBalanceMapper`、销售订单/出库 Mapper、`DataScopeService`、`AuditMetadataFactory` 和 `SystemLogService`。

新增 `InventoryReservationEventMapper` 负责写入预占事件。

改造 `InventoryPostingService` 的预占生命周期写事件：

- `reserve` 成功后写 `RESERVE` 事件。
- `releaseReservation` 成功后写 `RELEASE` 事件。
- 新增按预占 ID 释放的方法，供 `InventoryReservationOpsService` 在权限和数据范围校验后调用，并由 `InventoryPostingService` 统一写 `MANUAL_RELEASE` 事件。

事件写入必须与余额和预占状态变更在同一事务内完成。若事件写入失败，整个释放或预占操作回滚。

## 接口设计

统一路径：`/api/inventory/reservations`

- `GET /summary`：预占看板汇总。
- `GET /api/inventory/reservations`：分页查询预占列表。
- `GET /{id}`：预占详情。
- `GET /source`：按 `sourceType`、`sourceId` 或 `sourceNo` 反查预占和释放事件。
- `GET /checks`：一致性巡检问题清单。
- `POST /{id}/manual-release`：人工释放异常预占。

分页查询支持条件：

- `warehouseId`
- `productId`
- `sourceType`
- `sourceNo`
- `status`
- `createdTimeFrom`
- `createdTimeTo`
- `pageNo`
- `pageSize`

人工释放请求字段：

- `qty`：释放数量，必须大于 0 且不能超过 `remainingQty`。
- `reason`：释放原因，必填，最长 255。

## 权限与数据范围

新增权限码：

- `inventory:reservation:view`
- `inventory:reservation:check`
- `inventory:reservation:release`

权限映射：

- 查询、详情、来源追溯、看板使用 `inventory:reservation:view`。
- 巡检使用 `inventory:reservation:check`。
- 人工释放使用 `inventory:reservation:release`。

数据范围沿用仓库范围。用户没有某仓库库存可见权限时，不能查询该仓库预占，也不能释放该仓库预占。

## 巡检规则

巡检只读，不修改数据。

问题类型：

- `BALANCE_RESERVED_MISMATCH`：`inv_balance.qty_reserved` 与 `ACTIVE` 预占 `remaining_qty` 汇总不一致。
- `RESERVATION_QUANTITY_INVALID`：`reserved_qty != released_qty + remaining_qty`，或任一数量为负。
- `RESERVATION_BALANCE_MISSING`：预占关联的库存余额不存在。
- `RESERVATION_SOURCE_MISSING`：预占来源销售订单或销售订单明细不存在。
- `RESERVATION_SOURCE_STATUS_INVALID`：来源销售订单已取消、未审批或已全出库但预占仍有剩余。
- `BALANCE_AVAILABLE_NEGATIVE`：`qty_on_hand - qty_reserved < 0`。

巡检响应包括问题类型、严重级别、仓库、商品、预占 ID、来源单号、期望值、实际值和说明。

严重级别：

- `ERROR`：会影响库存可用量或订单履约。
- `WARN`：数据可疑但不一定影响当前库存。

## 人工释放流程

1. 校验权限和仓库数据范围。
2. 查询预占记录并校验公司隔离。
3. 校验 `remainingQty > 0`。
4. 校验释放数量大于 0 且不超过 `remainingQty`。
5. 使用乐观锁更新 `inv_reservation.released_qty`、`remaining_qty`、`status`、`updated_by`、`updated_time`。
6. 使用乐观锁扣减 `inv_balance.qty_reserved`。
7. 写入 `inv_reservation_event` 的 `MANUAL_RELEASE` 事件。
8. 写入系统审计日志，记录业务类型、预占 ID、来源单号、释放数量和原因。

若预占或余额乐观锁失败，返回“数据已被其他操作修改，请刷新后重试”。若余额预占量不足，返回“库存预占余额不足，不能释放预占”。

## 错误处理

- 参数错误返回业务异常消息，不返回堆栈。
- 权限不足返回 Spring Security 的访问拒绝。
- 乐观锁冲突使用已有 `BusinessConflictException` 或 `OptimisticLockGuard` 风格。
- 巡检接口不会因为单条异常数据中断整体结果，单条问题进入响应列表。

## 测试计划

- 迁移脚本测试：验证 `inv_reservation_event` 表和索引存在。
- 权限码测试：验证新增权限进入 `PermissionCodes.allPermissions()`。
- 查询服务测试：验证分页筛选、仓库数据范围、看板汇总。
- 巡检测试：覆盖余额不一致、数量不自洽、来源缺失、状态不匹配、负可用量。
- 人工释放测试：覆盖成功释放、原因必填、超量释放、无权限仓库、乐观锁冲突、事件和审计写入。
- 既有流程回归：销售订单审批写 `RESERVE` 事件，销售出库写 `RELEASE` 事件。

验收命令：

```powershell
.\mvnw.cmd test
.\mvnw.cmd -DskipTests package
```

## 上线注意事项

- 新表只追加事件，不改变既有预占数据语义。
- 迁移脚本不能重写历史预占事件，历史预占在上线后首次释放或后续新预占才产生事件。
- 若需要为历史预占补事件，应另做一次性数据治理脚本，本阶段不包含。
- 人工释放不能替代正常销售出库，操作入口要在前端明确标记为异常处理。
